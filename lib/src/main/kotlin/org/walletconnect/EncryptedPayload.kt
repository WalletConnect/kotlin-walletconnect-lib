package org.walletconnect

import com.google.gson.annotations.SerializedName
import com.squareup.moshi.Json

data class EncryptedPayload(

    @Json(name = "data")
    @SerializedName("data")
    val data: String,

    @Json(name = "iv")
    @SerializedName("iv")
    val iv: String,

    @Json(name = "hmac")
    @SerializedName("hmac")
    val hmac: String
)
