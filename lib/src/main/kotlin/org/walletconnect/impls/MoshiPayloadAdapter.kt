package org.walletconnect.impls

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.walletconnect.EncryptedPayload
import org.walletconnect.Session

class MoshiPayloadAdapter(moshi: Moshi) : BasePayloadAdapter() {

    private val payloadAdapter = moshi.adapter(EncryptedPayload::class.java)
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(
            Map::class.java,
            String::class.java,
            Any::class.java
        )
    )

    override fun parse(payload: String, key: String): Session.MethodCall {
        val encryptedPayload = payloadAdapter.fromJson(payload)
            ?: throw IllegalArgumentException("Invalid json payload!")

        val decryptedPayload = payloadEncryptionHelper.decryptPayload(encryptedPayload, key)

        val payloadJson = String(decryptedPayload)
        val jsonRpcMap = mapAdapter.fromJson(payloadJson)
        return parseJsonRpcMethodCall(jsonRpcMap)
    }

    override fun prepare(data: Session.MethodCall, key: String): String {
        val bytesData = mapAdapter.toJson(data.toJsonRpcMap()).toByteArray()
        val encryptedPayload = payloadEncryptionHelper.encryptPayload(bytesData, key)
        return payloadAdapter.toJson(encryptedPayload)
    }
}
