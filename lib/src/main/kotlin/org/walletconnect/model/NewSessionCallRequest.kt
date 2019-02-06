package org.walletconnect.model

data class NewSessionCallRequest(val dappName: String, val encryptionPayload: EncryptedData)