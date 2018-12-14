package org.walletconnect.model

data class EncryptedData(val data: String, val iv: String, val hmac: String)