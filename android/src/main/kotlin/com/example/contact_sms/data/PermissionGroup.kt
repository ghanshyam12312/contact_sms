package com.baseflow.permissionhandler.data

import com.google.gson.annotations.SerializedName

enum class PermissionGroup {
    @SerializedName("contacts")
    CONTACTS,
    @SerializedName("sms")
    SMS,
    @SerializedName("unknown")
    UNKNOWN,

}