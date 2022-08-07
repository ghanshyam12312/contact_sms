import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:collection/collection.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:quiver/core.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'package:url_launcher/url_launcher.dart';

part 'package:contact_sms/permission_enums.dart';
part 'package:contact_sms/utils/codec.dart';

class ContactsService extends PlatformInterface {
  static const MethodChannel _channel = MethodChannel('flutter_contacts');

  ContactsService() : super(token: _token);
  static final Object _token = Object();

  static ContactsService _instance = ContactsService();
  static ContactsService get instance => _instance;

  static set instance(ContactsService instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  /// Returns a [Future] containing the current permission status for the supplied [PermissionGroup].
  Future<PermissionStatus> checkPermissionStatus(
      PermissionGroup permission) async {
    final dynamic status = await _channel.invokeMethod(
        'checkPermissionStatus', Codec.encodePermissionGroup(permission));

    return Codec.decodePermissionStatus(status);
  }

  /// Open the App settings page.
  ///
  /// Returns [true] if the app settings page could be opened, otherwise [false] is returned.
  Future<bool> openAppSettings() async {
    final bool hasOpened = await _channel.invokeMethod('openAppSettings');
    return hasOpened;
  }

  /// Request the user for access to the supplied list of permissiongroups.
  ///
  /// Returns a [Map] containing the status per requested permissiongroup.
  Future<Map<PermissionGroup, PermissionStatus>> requestPermissions(
      List<PermissionGroup> permissions) async {
    final String jsonData = Codec.encodePermissionGroups(permissions);
    final dynamic status =
        await _channel.invokeMethod('requestPermissions', jsonData);

    return Codec.decodePermissionRequestResult(status);
  }

  /// Request to see if you should show a rationale for requesting permission.
  ///
  /// This method is only implemented on Android, calling this on iOS always
  /// returns [false].
  Future<bool> shouldShowRequestPermissionRationale(
      PermissionGroup permission) async {
    if (!Platform.isAndroid) {
      return false;
    }

    final bool shouldShowRationale = await _channel.invokeMethod(
        'shouldShowRequestPermissionRationale',
        Codec.encodePermissionGroup(permission));

    return shouldShowRationale;
  }

  /// Fetches all contacts, or when specified, the contacts with a name
  /// matching [query]
  static Future<List<Contact>> getContacts(
      {String? query,
      bool withThumbnails = true,
      bool photoHighResolution = true,
      bool orderByGivenName = true,
      bool iOSLocalizedLabels = true,
      bool androidLocalizedLabels = true}) async {
    Iterable contacts =
        await _channel.invokeMethod('getContacts', <String, dynamic>{
      'query': query,
      'withThumbnails': withThumbnails,
      'photoHighResolution': photoHighResolution,
      'orderByGivenName': orderByGivenName,
      'iOSLocalizedLabels': iOSLocalizedLabels,
      'androidLocalizedLabels': androidLocalizedLabels,
    });
    return contacts.map((m) => Contact.fromMap(m)).toList();
  }

  /// Loads the avatar for the given contact and returns it. If the user does
  /// not have an avatar, then `null` is returned in that slot. Only implemented
  /// on Android.
  static Future<Uint8List?> getAvatar(final Contact contact,
          {final bool photoHighRes = true}) =>
      _channel.invokeMethod('getAvatar', <String, dynamic>{
        'contact': Contact._toMap(contact),
        'photoHighResolution': photoHighRes,
      });

  ///
  ///
  Future<String> sendSMS({
    required String message,
    required String recipients,
    bool sendDirect = false,
  }) {
    final mapData = <dynamic, dynamic>{};
    mapData['message'] = message;
    if (!kIsWeb && Platform.isIOS) {
      mapData['recipients'] = recipients;
      return _channel
          .invokeMethod<String>('sendSMS', mapData)
          .then((value) => value ?? 'Error sending sms');
    } else {
      String _phones = recipients;
      mapData['recipients'] = _phones;
      mapData['sendDirect'] = sendDirect;
      return _channel
          .invokeMethod<String>('sendSMS', mapData)
          .then((value) => value ?? 'Error sending sms');
    }
  }

  Future<bool> canSendSMS() {
    return _channel
        .invokeMethod<bool>('canSendSMS')
        .then((value) => value ?? false);
  }

  Future<bool> launchSms(String? number, [String? body]) {
    // ignore: parameter_assignments
    number ??= '';
    if (body != null) {
      final _body = Uri.encodeComponent(body);
      return launch('sms:/$number${separator}body=$_body');
    }
    return launch('sms:/$number');
  }

  String get separator => Platform.isIOS || Platform.isMacOS ? '&' : '?';
}

class Contact {
  Contact({
    this.displayName,
    this.givenName,
    this.middleName,
    this.familyName,
    this.emails,
    this.phones,
    this.avatar,
  });

  String? identifier, displayName, givenName, middleName, familyName;
  List<Item>? emails = [];
  List<Item>? phones = [];

  Uint8List? avatar;

  String initials() {
    return ((this.givenName?.isNotEmpty == true ? this.givenName![0] : "") +
            (this.familyName?.isNotEmpty == true ? this.familyName![0] : ""))
        .toUpperCase();
  }

  Contact.fromMap(Map m) {
    identifier = m["identifier"];
    displayName = m["displayName"];
    givenName = m["givenName"];
    middleName = m["middleName"];
    familyName = m["familyName"];

    emails = (m["emails"] as List?)?.map((m) => Item.fromMap(m)).toList();
    phones = (m["phones"] as List?)?.map((m) => Item.fromMap(m)).toList();
    avatar = m["avatar"];
  }

  static Map _toMap(Contact contact) {
    var emails = [];
    for (Item email in contact.emails ?? []) {
      emails.add(Item._toMap(email));
    }
    var phones = [];
    for (Item phone in contact.phones ?? []) {
      phones.add(Item._toMap(phone));
    }

    return {
      "identifier": contact.identifier,
      "displayName": contact.displayName,
      "givenName": contact.givenName,
      "middleName": contact.middleName,
      "familyName": contact.familyName,
      "emails": emails,
      "phones": phones,
      "avatar": contact.avatar,
    };
  }

  Map toMap() {
    return Contact._toMap(this);
  }

  /// The [+] operator fills in this contact's empty fields with the fields from [other]
  operator +(Contact other) => Contact(
        givenName: this.givenName ?? other.givenName,
        middleName: this.middleName ?? other.middleName,
        familyName: this.familyName ?? other.familyName,
        emails: this.emails == null
            ? other.emails
            : this
                .emails!
                .toSet()
                .union(other.emails?.toSet() ?? Set())
                .toList(),
        phones: this.phones == null
            ? other.phones
            : this
                .phones!
                .toSet()
                .union(other.phones?.toSet() ?? Set())
                .toList(),
        avatar: this.avatar ?? other.avatar,
      );

  /// Returns true if all items in this contact are identical.
  @override
  bool operator ==(Object other) {
    return other is Contact &&
        this.avatar == other.avatar &&
        this.displayName == other.displayName &&
        this.givenName == other.givenName &&
        this.familyName == other.familyName &&
        this.identifier == other.identifier &&
        this.middleName == other.middleName &&
        DeepCollectionEquality.unordered().equals(this.phones, other.phones) &&
        DeepCollectionEquality.unordered().equals(this.emails, other.emails);
  }

  @override
  int get hashCode {
    return hashObjects([
      this.displayName,
      this.familyName,
      this.givenName,
      this.identifier,
      this.middleName,
    ].where((s) => s != null));
  }

  AndroidAccountType? accountTypeFromString(String? androidAccountType) {
    if (androidAccountType == null) {
      return null;
    }
    if (androidAccountType.startsWith("com.google")) {
      return AndroidAccountType.google;
    } else if (androidAccountType.startsWith("com.whatsapp")) {
      return AndroidAccountType.whatsapp;
    } else if (androidAccountType.startsWith("com.facebook")) {
      return AndroidAccountType.facebook;
    }

    /// Other account types are not supported on Android
    /// such as Samsung, htc etc...
    return AndroidAccountType.other;
  }
}

/// Item class used for contact fields which only have a [label] and
/// a [value], such as emails and phone numbers
class Item {
  Item({this.label, this.value});

  String? label, value;

  Item.fromMap(Map m) {
    label = m["label"];
    value = m["value"];
  }

  @override
  bool operator ==(Object other) {
    return other is Item &&
        this.label == other.label &&
        this.value == other.value;
  }

  @override
  int get hashCode => hash2(label ?? "", value ?? "");

  static Map _toMap(Item i) => {"label": i.label, "value": i.value};
}

// Open SMS Dialog on iOS/Android/Web
Future<String> sendSMS({
  required String message,
  required String recipients,
  bool sendDirect = false,
}) =>
    ContactsService.instance.sendSMS(
      message: message,
      recipients: recipients,
      sendDirect: sendDirect,
    );

/// Launch SMS Url Scheme on all platforms
Future<bool> launchSms({
  String? message,
  String? number,
}) =>
    ContactsService.instance.launchSms(number, message);

/// Check if you can send SMS on this platform
Future<bool> canSendSMS() => ContactsService.instance.canSendSMS();

enum AndroidAccountType { facebook, google, whatsapp, other }
