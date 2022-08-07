package com.example.contact_sms

import android.content.res.Resources
import android.database.Cursor
import android.provider.ContactsContract
import java.util.*

/***
 * Represents an object which has a label and a value
 * such as an email or a phone
 */
class Item(var label: String?, var value: String?, var type: Int) {
    fun toMap(): HashMap<String, String?> {
        val result = HashMap<String, String?>()
        result["label"] = label
        result["value"] = value
        result["type"] = type.toString()
        return result
    }

    companion object {
        fun fromMap(map: HashMap<String?, String?>): Item {
            val label = map["label"]
            val value = map["value"]
            val type = map["type"]
            return Item(label, value, type?.toInt() ?: -1)
        }

        fun getPhoneLabel(
            resources: Resources?,
            type: Int,
            cursor: Cursor,
            localizedLabels: Boolean
        ): String {
            return if (localizedLabels) {
                val localizedLabel = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                    resources,
                    type,
                    ""
                )
                localizedLabel.toString().lowercase(Locale.getDefault())
            } else {
                when (type) {
                    ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "home"
                    ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "work"
                    ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "mobile"
                    ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK -> "fax work"
                    ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME -> "fax home"
                    ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> "main"
                    ContactsContract.CommonDataKinds.Phone.TYPE_COMPANY_MAIN -> "company"
                    ContactsContract.CommonDataKinds.Phone.TYPE_PAGER -> "pager"
                    ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM -> if (cursor.getString(
                            cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL)
                        ) != null
                    ) {
                        cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL))
                            .lowercase(Locale.getDefault())
                    } else ""
                    else -> "other"
                }
            }
        }

        fun getEmailLabel(
            resources: Resources?,
            type: Int,
            cursor: Cursor,
            localizedLabels: Boolean
        ): String {
            return if (localizedLabels) {
                val localizedLabel = ContactsContract.CommonDataKinds.Email.getTypeLabel(
                    resources,
                    type,
                    ""
                )
                localizedLabel.toString().lowercase(Locale.getDefault())
            } else {
                when (type) {
                    ContactsContract.CommonDataKinds.Email.TYPE_HOME -> "home"
                    ContactsContract.CommonDataKinds.Email.TYPE_WORK -> "work"
                    ContactsContract.CommonDataKinds.Email.TYPE_MOBILE -> "mobile"
                    ContactsContract.CommonDataKinds.Email.TYPE_CUSTOM -> if (cursor.getString(
                            cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.LABEL)
                        ) != null
                    ) {
                        cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.LABEL))
                            .lowercase(Locale.getDefault())
                    } else ""
                    else -> "other"
                }
            }
        }
    }
}