package com.example.contact_sms

import java.util.*

class Contact : Comparable<Contact?> {
    internal constructor(id: String?) {
        identifier = id
    }

    private constructor() {}

    var identifier: String? = null
    var displayName: String? = null
    @JvmField
    var givenName: String? = null
    var middleName: String? = null
    var familyName: String? = null
    var emails = ArrayList<Item>()
    var phones = ArrayList<Item>()
    var avatar: ByteArray? = ByteArray(0)
    fun toMap(): HashMap<String, Any?> {
        val contactMap = HashMap<String, Any?>()
        contactMap["identifier"] = identifier
        contactMap["displayName"] = displayName
        contactMap["givenName"] = givenName
        contactMap["middleName"] = middleName
        contactMap["familyName"] = familyName
        contactMap["avatar"] = avatar
        val emailsMap = ArrayList<HashMap<String, String?>>()
        for (email in emails) {
            emailsMap.add(email.toMap())
        }
        contactMap["emails"] = emailsMap
        val phonesMap = ArrayList<HashMap<String, String?>>()
        for (phone in phones) {
            phonesMap.add(phone.toMap())
        }
        contactMap["phones"] = phonesMap
        return contactMap
    }

    override fun compareTo(contact: Contact?): Int {
        val givenName1 = if (givenName == null) "" else givenName!!.lowercase(Locale.getDefault())
        val givenName2 =
            if (contact == null) "" else if (contact.givenName == null) "" else contact.givenName!!.lowercase(
                Locale.getDefault()
            )
        return givenName1.compareTo(givenName2)
    }

    companion object {
        fun fromMap(map: HashMap<*, *>): Contact {
            val contact = Contact()
            contact.identifier = map["identifier"] as String?
            contact.givenName = map["givenName"] as String?
            contact.middleName = map["middleName"] as String?
            contact.familyName = map["familyName"] as String?
            contact.avatar = map["avatar"] as ByteArray?
            val emails = map["emails"] as ArrayList<HashMap<String?, String?>>?
            if (emails != null) {
                for (email in emails) {
                    contact.emails.add(Item.fromMap(email))
                }
            }
            val phones = map["phones"] as ArrayList<HashMap<String?, String?>>?
            if (phones != null) {
                for (phone in phones) {
                    contact.phones.add(Item.fromMap(phone))
                }
            }
            return contact
        }
    }
}