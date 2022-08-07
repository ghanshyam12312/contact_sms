package com.example.contact_sms

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.provider.BaseColumns
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.text.TextUtils
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.baseflow.permissionhandler.data.PermissionGroup
import com.baseflow.permissionhandler.data.PermissionStatus
import com.baseflow.permissionhandler.utils.Codec
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.FlutterPlugin.FlutterPluginBinding
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.PluginRegistry
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener
import io.flutter.plugin.common.PluginRegistry.Registrar
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit


class ContactSmsPlugin(private var requestedPermissions: MutableList<String>? = null) : MethodCallHandler, FlutterPlugin, ActivityAware {


  private var contentResolver: ContentResolver? = null
  private var methodChannel: MethodChannel? = null
  private var activity: Activity? = null
  private var delegate: BaseContactsServiceDelegate? = null
  private var resources: Resources? = null
  private val executor: ExecutorService =
    ThreadPoolExecutor(0, 10, 60, TimeUnit.SECONDS, ArrayBlockingQueue(1000))
  private var mResult: MethodChannel.Result? = null
  private var mRequestResults = mutableMapOf<PermissionGroup, PermissionStatus>()
  private fun initDelegateWithRegister(registrar: Registrar) {
    delegate = ContactServiceDelegateOld(registrar)
  }

  private fun initInstance(messenger: BinaryMessenger, context: Context) {
    methodChannel = MethodChannel(messenger, "flutter_contacts")
    methodChannel!!.setMethodCallHandler(this)
    contentResolver = context.contentResolver
  }

  override fun onAttachedToEngine(binding: FlutterPluginBinding) {
    resources = binding.applicationContext.resources
    initInstance(binding.binaryMessenger, binding.applicationContext)
    delegate = ContactServiceDelegate(binding.applicationContext)
  }

  override fun onDetachedFromEngine(binding: FlutterPluginBinding) {
    methodChannel!!.setMethodCallHandler(null)
    methodChannel = null
    contentResolver = null
    delegate = null
    resources = null
  }

  private fun getManifestNames(permission: PermissionGroup): List<String>? {
    val permissionNames: MutableList<String> = mutableListOf()

    when (permission) {


      PermissionGroup.CONTACTS -> {
        if (hasPermissionInManifest(Manifest.permission.READ_CONTACTS))
          permissionNames.add(Manifest.permission.READ_CONTACTS)

        if (hasPermissionInManifest(Manifest.permission.WRITE_CONTACTS))
          permissionNames.add(Manifest.permission.WRITE_CONTACTS)

        if (hasPermissionInManifest(Manifest.permission.GET_ACCOUNTS))
          permissionNames.add(Manifest.permission.GET_ACCOUNTS)
      }


      PermissionGroup.SMS -> {
        if (hasPermissionInManifest(Manifest.permission.SEND_SMS))
          permissionNames.add(Manifest.permission.SEND_SMS)

        if (hasPermissionInManifest(Manifest.permission.RECEIVE_SMS))
          permissionNames.add(Manifest.permission.RECEIVE_SMS)

        if (hasPermissionInManifest(Manifest.permission.READ_SMS))
          permissionNames.add(Manifest.permission.READ_SMS)

        if (hasPermissionInManifest(Manifest.permission.RECEIVE_WAP_PUSH))
          permissionNames.add(Manifest.permission.RECEIVE_WAP_PUSH)

        if (hasPermissionInManifest(Manifest.permission.RECEIVE_MMS))
          permissionNames.add(Manifest.permission.RECEIVE_MMS)
      }
      else -> return null
    }

    return permissionNames
  }

  private fun hasPermissionInManifest(permission: String): Boolean {
    try {
      requestedPermissions?.let {
        return it.any { r -> r.equals(permission, true) }
      }


      if (activity == null) {
        Log.d(mLogTag, "Unable to detect current Activity or App Context.")
        return false
      }

      val info: PackageInfo? = activity!!.packageManager.getPackageInfo(activity!!.packageName, PackageManager.GET_PERMISSIONS)

      if (info == null) {
        Log.d(mLogTag, "Unable to get Package info, will not be able to determine permissions to request.")
        return false
      }

      requestedPermissions = info.requestedPermissions.toMutableList()

      if (requestedPermissions == null) {
        Log.d(mLogTag, "There are no requested permissions, please check to ensure you have marked permissions you want to request.")
        return false
      }

      requestedPermissions?.let {
        return it.any { r -> r.equals(permission, true) }
      } ?: return false
    } catch (ex: Exception) {
      Log.d(mLogTag, "Unable to check manifest for permission: $ex")
    }
    return false
  }

  private fun checkPermissionStatus(permission: PermissionGroup): PermissionStatus {
    val names = getManifestNames(permission)

    if (names == null) {
      Log.d(mLogTag, "No android specific permissions needed for: $permission")

      return PermissionStatus.GRANTED
    }

    //if no permissions were found then there is an issue and permission is not set in Android manifest
    if (names.count() == 0) {
      Log.d(mLogTag, "No permissions found in manifest for: $permission")
      return PermissionStatus.UNKNOWN
    }


    if (activity == null) {
      Log.d(mLogTag, "Unable to detect current Activity or App Context.")
      return PermissionStatus.UNKNOWN
    }

    val targetsMOrHigher = activity!!.applicationInfo.targetSdkVersion >= android.os.Build.VERSION_CODES.M

    for (name in names) {
      if (targetsMOrHigher && ContextCompat.checkSelfPermission(activity!!, name) != PackageManager.PERMISSION_GRANTED) {
        return PermissionStatus.DENIED
      }
    }

    return PermissionStatus.GRANTED
  }

  private fun shouldShowRequestPermissionRationale(permission: PermissionGroup) : Boolean {

    if(activity == null)
    {
      Log.d(mLogTag, "Unable to detect current Activity.")
      return false
    }

    val names = getManifestNames(permission)

    // if isn't an android specific group then go ahead and return false;
    if (names == null)
    {
      Log.d(mLogTag, "No android specific permissions needed for: $permission")
      return false
    }

    if (names.isEmpty())
    {
      Log.d(mLogTag,"No permissions found in manifest for: $permission no need to show request rationale")
      return false
    }

    for(name in names)
    {
      return ActivityCompat.shouldShowRequestPermissionRationale(activity!!, name)
    }

    return false
  }

  private fun requestPermissions(permissions: Array<PermissionGroup>) {
    if (activity == null) {
      Log.d(mLogTag, "Unable to detect current Activity.")

      for (permission in permissions) {
        mRequestResults[permission] = PermissionStatus.UNKNOWN
      }

      processResult()
      return
    }

    val permissionsToRequest = mutableListOf<String>()
    for (permission in permissions) {
      val permissionStatus = checkPermissionStatus(permission)

      if (permissionStatus != PermissionStatus.GRANTED) {
        val names = getManifestNames(permission)

        //check to see if we can find manifest names
        //if we can't add as unknown and continue
        if (names == null || names.isEmpty()) {
          if (!mRequestResults.containsKey(permission)) {
            mRequestResults[permission] = PermissionStatus.UNKNOWN
          }

          continue
        }

        names.let { permissionsToRequest.addAll(it) }
      } else {
        if (!mRequestResults.containsKey(permission)) {
          mRequestResults[permission] = PermissionStatus.GRANTED
        }
      }
    }

    if (permissionsToRequest.count() > 0) {
      ActivityCompat.requestPermissions(
        activity!!,
        permissionsToRequest.toTypedArray(),
        permissionCode)
    } else if (mRequestResults.count() > 0) {
      processResult()
    }
  }



  private fun Int.toPermissionStatus(): PermissionStatus {
    return if (this == PackageManager.PERMISSION_GRANTED) PermissionStatus.GRANTED else PermissionStatus.DENIED
  }

  private fun handleSuccess(permissionStatus: PermissionStatus, result: MethodChannel.Result?) {
    result?.success(Codec.encodePermissionStatus(permissionStatus))
  }

  private fun openAppSettings(): Boolean {

    if (activity == null) {
      Log.d(mLogTag, "Unable to detect current Activity or App Context.")
      return false
    }

    return try {
      val settingsIntent = Intent()
      settingsIntent.action = android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
      settingsIntent.addCategory(Intent.CATEGORY_DEFAULT)
      settingsIntent.data = android.net.Uri.parse("package:" + activity!!.packageName)
      settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
      settingsIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)

      activity!!.startActivity(settingsIntent)

      true
    } catch(ex: Exception) {
      false
    }
  }


  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    when (call.method) {
      "checkPermissionStatus" -> {
        val permission = Codec.decodePermissionGroup(call.arguments)
        val permissionStatus = checkPermissionStatus(permission)
        handleSuccess(permissionStatus, result)
      }
      "requestPermissions" -> {
        if (mResult != null) {
          result.error(
            "ERROR_ALREADY_REQUESTING_PERMISSIONS",
            "A request for permissions is already running, please wait for it to finish before doing another request (note that you can request multiple permissions at the same time).",
            null)
        }

        mResult = result
        val permissions = Codec.decodePermissionGroups(call.arguments)
        requestPermissions(permissions)
      }
      "shouldShowRequestPermissionRationale" -> {
        val permission = Codec.decodePermissionGroup(call.arguments)
        result.success(shouldShowRequestPermissionRationale(permission))
      }
      "openAppSettings" -> {
        val isOpen = openAppSettings()
        result.success(isOpen)
      }
      "getContacts" -> {
        getContacts(
          call.method,
          call.argument<Any>("query") as String?,
          call.argument<Any>("withThumbnails") as Boolean,
          call.argument<Any>("photoHighResolution") as Boolean,
          call.argument<Any>("orderByGivenName") as Boolean,
          call.argument<Any>("androidLocalizedLabels") as Boolean,
          result
        )
      }
      "getAvatar" -> {
        val contact = (call.argument<Any>("contact") as HashMap<*, *>?)?.let {
          Contact.fromMap(
            it
          )
        }
        getAvatar(contact, call.argument<Any>("photoHighResolution") as Boolean, result)
      }
      "openExistingContact" -> {
        val contact = (call.argument<Any>("contact") as HashMap<*, *>?)?.let {
          Contact.fromMap(
            it
          )
        }
        val localizedLabels = call.argument<Boolean>("androidLocalizedLabels")!!
        if (delegate != null) {
          delegate!!.setResult(result)
          delegate!!.setLocalizedLabels(localizedLabels)
          delegate!!.openExistingContact(contact)
        } else {
          result.success(FORM_COULD_NOT_BE_OPEN)
        }
      }
      "sendSMS" -> {
        run {
          if (!canSendSMS()) {
            result.error(
              "device_not_capable",
              "The current device is not capable of sending text messages.",
              "A device may be unable to send messages if it does not support messaging or if it is not currently configured to send messages. This only applies to the ability to send text messages via iMessage, SMS, and MMS."
            )
            return
          }
          val message = call.argument<String>("message")
          val recipients = call.argument<String>("recipients")
          val sendDirect = call.argument<Boolean>("sendDirect")
          sendSMS(result, recipients, message, sendDirect)
        }
        run { result.success(canSendSMS()) }
        run { result.notImplemented() }
      }
      "canSendSMS" -> {
        run { result.success(canSendSMS()) }
        run { result.notImplemented() }
      }
      else -> {
        result.notImplemented()
      }
    }
  }

  private fun canSendSMS(): Boolean {
    if (!activity!!.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) return false
    val intent = Intent(Intent.ACTION_SENDTO)
    intent.data = Uri.parse("smsto:")
    val activityInfo = intent.resolveActivityInfo(activity!!.packageManager, intent.flags)
    return !(activityInfo == null || !activityInfo.exported)
  }

  private fun sendSMS(
    result: MethodChannel.Result,
    phones: String?,
    message: String?,
    sendDirect: Boolean?
  ) {
    if (sendDirect!!) {
      sendSMSDirect(result, phones, message)
    }
  }

  private fun sendSMSDirect(result: MethodChannel.Result, phones: String?, message: String?) {
    // SmsManager is android.telephony
    val sentIntent = PendingIntent.getBroadcast(
      activity,
      0,
      Intent("SMS_SENT_ACTION"),
      PendingIntent.FLAG_IMMUTABLE
    )
    val mSmsManager = SmsManager.getDefault()
    val numbers = phones!!.split(";").toTypedArray()
    for (num in numbers) {
      Log.d("Flutter SMS", "msg.length() : " + message!!.toByteArray().size)
      if (message.toByteArray().size > 80) {
        val partMessage = mSmsManager.divideMessage(message)
        mSmsManager.sendMultipartTextMessage(num, null, partMessage, null, null)
      } else {
        mSmsManager.sendTextMessage(num, null, message, sentIntent, null)
      }
    }
    result.success("SMS Sent!")
  }

  private fun getContacts(
    callMethod: String,
    query: String?,
    withThumbnails: Boolean,
    photoHighResolution: Boolean,
    orderByGivenName: Boolean,
    localizedLabels: Boolean,
    result: MethodChannel.Result
  ) {
    GetContactsTask(
      callMethod,
      result,
      withThumbnails,
      photoHighResolution,
      orderByGivenName,
      localizedLabels
    ).executeOnExecutor(executor, query, false)
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    if (delegate is ContactServiceDelegate) {
      (delegate as ContactServiceDelegate).bindToActivity(binding)
      activity = binding.activity
    }
  }

  override fun onDetachedFromActivityForConfigChanges() {
    if (delegate is ContactServiceDelegate) {
      (delegate as ContactServiceDelegate).unbindActivity()
    }
    activity = null
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    if (delegate is ContactServiceDelegate) {
      (delegate as ContactServiceDelegate).bindToActivity(binding)
    }
    activity = binding.activity
  }

  override fun onDetachedFromActivity() {
    if (delegate is ContactServiceDelegate) {
      (delegate as ContactServiceDelegate).unbindActivity()
    }
    activity = null
  }

  private open inner class BaseContactsServiceDelegate : ActivityResultListener {
    private var result: MethodChannel.Result? = null
    private var localizedLabels = false
    fun setResult(result: MethodChannel.Result?) {
      this.result = result
    }

    fun setLocalizedLabels(localizedLabels: Boolean) {
      this.localizedLabels = localizedLabels
    }

    fun finishWithResult(result: Any?) {
      if (this.result != null) {
        this.result!!.success(result)
        this.result = null
      }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?): Boolean {
      if (requestCode == REQUEST_OPEN_EXISTING_CONTACT) {
        try {
          val ur = intent!!.data
          finishWithResult(getContactByIdentifier(ur!!.lastPathSegment))
        } catch (e: NullPointerException) {
          finishWithResult(FORM_OPERATION_CANCELED)
        }
        return true
      }
      finishWithResult(FORM_COULD_NOT_BE_OPEN)
      return false
    }

    fun openExistingContact(contact: Contact?) {
      val identifier = contact?.identifier
      try {
        val contactMapFromDevice = getContactByIdentifier(identifier)
        // Contact existence check
        if (contactMapFromDevice != null) {
          val uri =
            Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, identifier)
          val intent = Intent(Intent.ACTION_EDIT)
          intent.setDataAndType(uri, ContactsContract.Contacts.CONTENT_ITEM_TYPE)
          intent.putExtra("finishActivityOnSaveCompleted", true)
          startIntent(intent)
        } else {
          finishWithResult(FORM_COULD_NOT_BE_OPEN)
        }
      } catch (e: Exception) {
        finishWithResult(FORM_COULD_NOT_BE_OPEN)
      }
    }

    open fun startIntent(intent: Intent) {}
    fun getContactByIdentifier(identifier: String?): HashMap<*, *>? {
      var matchingContacts: ArrayList<Contact>
      run {
        val cursor = contentResolver!!.query(
          ContactsContract.Data.CONTENT_URI, PROJECTION,
          ContactsContract.RawContacts.CONTACT_ID + " = ?", arrayOf(identifier),
          null
        )
        matchingContacts = try {
          getContactsFrom(cursor, localizedLabels)
        } finally {
          cursor?.close()
        }
      }
      return if (matchingContacts.size > 0) {
        matchingContacts.iterator().next().toMap()
      } else null
    }


  }

  private inner class ContactServiceDelegateOld internal constructor(private val registrar: Registrar) :
    BaseContactsServiceDelegate() {
    override fun startIntent(intent: Intent) {
      if (registrar.activity() != null) {
        registrar.activity()!!
          .startActivityForResult(intent, REQUEST_OPEN_EXISTING_CONTACT)
      } else {
        registrar.context().startActivity(intent)
      }
    }

    init {
      registrar.addActivityResultListener(this)
    }
  }

  private inner class ContactServiceDelegate internal constructor(private val context: Context) :
    BaseContactsServiceDelegate() {
    private var activityPluginBinding: ActivityPluginBinding? = null
    fun bindToActivity(activityPluginBinding: ActivityPluginBinding?) {
      this.activityPluginBinding = activityPluginBinding
      this.activityPluginBinding!!.addActivityResultListener(this)
    }

    fun unbindActivity() {
      activityPluginBinding!!.removeActivityResultListener(this)
      activityPluginBinding = null
    }

    override fun startIntent(intent: Intent) {
      if (activityPluginBinding != null) {
        if (intent.resolveActivity(context.packageManager) != null) {
          activityPluginBinding!!.activity.startActivityForResult(
            intent,
            REQUEST_OPEN_EXISTING_CONTACT
          )
        } else {
          finishWithResult(FORM_COULD_NOT_BE_OPEN)
        }
      } else {
        context.startActivity(intent)
      }
    }
  }

  private inner class GetContactsTask(
    private val callMethod: String,
    private val getContactResult: MethodChannel.Result,
    private val withThumbnails: Boolean,
    private val photoHighResolution: Boolean,
    private val orderByGivenName: Boolean,
    private val localizedLabels: Boolean
  ) : AsyncTask<Any?, Void?, ArrayList<HashMap<*, *>>?>() {

    override fun doInBackground(vararg params: Any?): ArrayList<HashMap<*, *>>? {
      val contacts: ArrayList<Contact>
      contacts = when (callMethod) {
        "getContacts" -> getContactsFrom(
          getCursor(params[0] as String?, null),
          localizedLabels
        )
        else -> return null
      }
      if (withThumbnails) {
        for (c in contacts) {
          val avatar = c.identifier?.let {
            loadContactPhotoHighRes(
              it, photoHighResolution, contentResolver
            )
          }
          if (avatar != null) {
            c.avatar = avatar
          } else {
            // To stay backwards-compatible, return an empty byte array rather than `null`.
            c.avatar = ByteArray(0)
          }
          //          if ((Boolean) params[3])
//              loadContactPhotoHighRes(c.identifier, (Boolean) params[3]);
//          else
//              setAvatarDataForContactIfAvailable(c);
        }
      }
      if (orderByGivenName) {
        val compareByGivenName =
          Comparator<Contact> { contactA, contactB -> contactA.compareTo(contactB) }
        Collections.sort(contacts, compareByGivenName)
      }

      //Transform the list of contacts to a list of Map
      val contactMaps = ArrayList<HashMap<*, *>>()
      for (c in contacts) {
        contactMaps.add(c.toMap())
      }
      return contactMaps
    }

    override fun onPostExecute(result: ArrayList<HashMap<*, *>>?) {
      if (result == null) {
        getContactResult.notImplemented()
      } else {
        getContactResult.success(result)
      }
    }
  }

  private fun getCursor(query: String?, rawContactId: String?): Cursor? {
    var selection =
      ("(" + ContactsContract.Data.MIMETYPE + "=? OR " + ContactsContract.Data.MIMETYPE + "=? OR "
              + ContactsContract.Data.MIMETYPE + "=? OR " + ContactsContract.Data.MIMETYPE + "=? OR "
              + ContactsContract.Data.MIMETYPE + "=? OR " + ContactsContract.Data.MIMETYPE + "=? OR "
              + ContactsContract.Data.MIMETYPE + "=? OR " + ContactsContract.RawContacts.ACCOUNT_TYPE + "=?" + ")")
    var selectionArgs = ArrayList(
      Arrays.asList(
        ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE,
        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
        ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
        ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,
        ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
        ContactsContract.RawContacts.ACCOUNT_TYPE
      )
    )
    if (query != null) {
      selectionArgs = ArrayList()
      selectionArgs.add("$query%")
      selection = ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " LIKE ?"
    }
    if (rawContactId != null) {
      selectionArgs.add(rawContactId)
      selection += " AND " + ContactsContract.Data.CONTACT_ID + " =?"
    }
    return contentResolver!!.query(
      ContactsContract.Data.CONTENT_URI,
      PROJECTION,
      selection,
      selectionArgs.toTypedArray(),
      null
    )
  }

  private fun getCursorForPhone(phone: String): Cursor? {
    if (phone.isEmpty()) return null
    val uri =
      Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phone))
    val projection = arrayOf(BaseColumns._ID)
    val contactIds = ArrayList<String>()
    val phoneCursor = contentResolver!!.query(uri, projection, null, null, null)
    while (phoneCursor != null && phoneCursor.moveToNext()) {
      contactIds += phoneCursor.getString(phoneCursor.getColumnIndex(BaseColumns._ID))
    }
    phoneCursor?.close()
    if (!contactIds.isEmpty()) {
      val contactIdsListString = contactIds.toString().replace("[", "(").replace("]", ")")
      val contactSelection = ContactsContract.Data.CONTACT_ID + " IN " + contactIdsListString
      return contentResolver!!.query(
        ContactsContract.Data.CONTENT_URI,
        PROJECTION,
        contactSelection,
        null,
        null
      )
    }
    return null
  }

  private fun getCursorForEmail(email: String): Cursor? {
    if (email.isEmpty()) return null
    val selectionArgs = ArrayList(
      Arrays.asList(
        "%$email%"
      )
    )
    val selection = ContactsContract.CommonDataKinds.Email.ADDRESS + " LIKE ?"
    return contentResolver!!.query(
      ContactsContract.Data.CONTENT_URI,
      PROJECTION,
      selection,
      selectionArgs.toTypedArray(),
      null
    )
  }

  /**
   * Builds the list of contacts from the cursor
   * @param cursor
   * @return the list of contacts
   */
  @SuppressLint("Range")
  private fun getContactsFrom(cursor: Cursor?, localizedLabels: Boolean): ArrayList<Contact> {
    val map: HashMap<String, Contact> = LinkedHashMap()
    while (cursor != null && cursor.moveToNext()) {
      val columnIndex = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID)
      val contactId = cursor.getString(columnIndex)
      if (!map.containsKey(contactId)) {
        map[contactId] = Contact(contactId)
      }
      val contact = map[contactId]
      val mimeType = cursor.getString(cursor.getColumnIndex(ContactsContract.Data.MIMETYPE))
      contact!!.displayName =
        cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME))
      //NAMES
      if (mimeType == ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE) {
        contact.givenName =
          cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME))
        contact.middleName =
          cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME))
        contact.familyName =
          cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME))
      } else if (mimeType == ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE) {
        val phoneNumber =
          cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
        if (!TextUtils.isEmpty(phoneNumber)) {
          val type =
            cursor.getInt(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE))
          val label = Item.getPhoneLabel(resources, type, cursor, localizedLabels)
          contact.phones.add(Item(label, phoneNumber, type))
        }
      } else if (mimeType == ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE) {
        val email =
          cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS))
        val type =
          cursor.getInt(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.TYPE))
        if (!TextUtils.isEmpty(email)) {
          val label = Item.getEmailLabel(resources, type, cursor, localizedLabels)
          contact.emails.add(Item(label, email, type))
        }
      }
    }
    cursor?.close()
    return ArrayList(map.values)
  }

  private fun setAvatarDataForContactIfAvailable(contact: Contact) {
    val contactUri = contact.identifier?.toInt()?.toLong()?.let {
      ContentUris.withAppendedId(
        ContactsContract.Contacts.CONTENT_URI,
        it
      )
    }
    val photoUri =
      Uri.withAppendedPath(contactUri, ContactsContract.Contacts.Photo.CONTENT_DIRECTORY)
    val avatarCursor = contentResolver!!.query(
      photoUri,
      arrayOf(ContactsContract.Contacts.Photo.PHOTO),
      null,
      null,
      null
    )
    if (avatarCursor != null && avatarCursor.moveToFirst()) {
      val avatar = avatarCursor.getBlob(0)
      contact.avatar = avatar
    }
    avatarCursor?.close()
  }

  private fun getAvatar(
    contact: Contact?, highRes: Boolean,
    result: MethodChannel.Result
  ) {
    contact?.let { GetAvatarsTask(it, highRes, contentResolver, result).executeOnExecutor(executor) }
  }

  private class GetAvatarsTask internal constructor(
    val contact: Contact, val highRes: Boolean,
    val contentResolver: ContentResolver?, val result: MethodChannel.Result
  ) : AsyncTask<Void?, Void?, ByteArray?>() {
    override fun doInBackground(vararg p0: Void?): ByteArray?  {
      // Load avatar for each contact identifier.
      return contact.identifier?.let { loadContactPhotoHighRes(it, highRes, contentResolver) }
    }

    override fun onPostExecute(avatar: ByteArray?) {
      result.success(avatar)
    }
  }

  private fun handlePermissionsRequest(permissions: Array<String>, grantResults: IntArray) {
    if (mResult == null) {
      return
    }

    for (i in permissions.indices) {
      val permission = parseManifestName(permissions[i])
      if (permission == PermissionGroup.UNKNOWN)
        continue

    }

    processResult()
  }
  private fun processResult() {
    mResult?.success(Codec.encodePermissionRequestResult(mRequestResults))

    mRequestResults.clear()
    mResult = null
  }

  companion object {
    private const val REQUEST_OPEN_CONTACT_FORM = 52941
    private const val REQUEST_OPEN_EXISTING_CONTACT = 52942
    private const val REQUEST_OPEN_CONTACT_PICKER = 52943
    private const val FORM_OPERATION_CANCELED = 1
    private const val FORM_COULD_NOT_BE_OPEN = 2
    private const val LOG_TAG = "flutter_contacts"

    const val permissionCode = 25

    @JvmStatic
    private val mLogTag = "permissions_handler"

    @JvmStatic
    fun registerWith(registrar: Registrar) {
      val instance = ContactSmsPlugin()
      instance.initInstance(registrar.messenger(), registrar.context())
      instance.initDelegateWithRegister(registrar)
      registrar.addRequestPermissionsResultListener(PluginRegistry.RequestPermissionsResultListener { id, permissions, grantResults ->
        if (id == permissionCode) {
          instance.handlePermissionsRequest(permissions, grantResults)

          return@RequestPermissionsResultListener true
        }

        return@RequestPermissionsResultListener false
      })
    }


    @JvmStatic
    fun parseManifestName(permission: String): PermissionGroup {

      when (permission) {
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS,
        Manifest.permission.GET_ACCOUNTS ->
          return PermissionGroup.CONTACTS
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS->
          return PermissionGroup.UNKNOWN
      }

      return PermissionGroup.UNKNOWN
    }


    private val PROJECTION = arrayOf(
      ContactsContract.Data.CONTACT_ID,
      ContactsContract.Profile.DISPLAY_NAME,
      ContactsContract.Contacts.Data.MIMETYPE,
      ContactsContract.RawContacts.ACCOUNT_TYPE,
      ContactsContract.RawContacts.ACCOUNT_NAME,
      ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
      ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
      ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME,
      ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME
    )

    private fun loadContactPhotoHighRes(
      identifier: String,
      photoHighResolution: Boolean, contentResolver: ContentResolver?
    ): ByteArray? {
      return try {
        val uri = ContentUris.withAppendedId(
          ContactsContract.Contacts.CONTENT_URI,
          identifier.toLong()
        )
        val input = ContactsContract.Contacts.openContactPhotoInputStream(
          contentResolver,
          uri,
          photoHighResolution
        )
          ?: return null
        val bitmap = BitmapFactory.decodeStream(input)
        input.close()
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val bytes = stream.toByteArray()
        stream.close()
        bytes
      } catch (ex: IOException) {
        Log.e(LOG_TAG, ex.message!!)
        null
      }
    }
  }

}