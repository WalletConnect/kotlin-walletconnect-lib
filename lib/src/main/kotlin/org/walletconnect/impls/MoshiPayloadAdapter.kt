package org.walletconnect.impls

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.modes.CBCBlockCipher
import org.bouncycastle.crypto.paddings.PKCS7Padding
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import org.komputing.khex.decode
import org.komputing.khex.extensions.toNoPrefixHexString
import org.walletconnect.Session
import org.walletconnect.types.*
import java.security.SecureRandom

class MoshiPayloadAdapter(moshi: Moshi) : Session.PayloadAdapter {

    private val payloadAdapter = moshi.adapter(EncryptedPayload::class.java)
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(
            Map::class.java,
            String::class.java,
            Any::class.java
        )
    )

    private fun createRandomBytes(i: Int) = ByteArray(i).also { SecureRandom().nextBytes(it) }

    override fun parse(payload: String, key: String): Session.MethodCall {
        val encryptedPayload = payloadAdapter.fromJson(payload) ?: throw IllegalArgumentException("Invalid json payload!")

        // TODO verify hmac

        val padding = PKCS7Padding()
        val aes = PaddedBufferedBlockCipher(
            CBCBlockCipher(AESEngine()),
            padding
        )
        val ivAndKey = ParametersWithIV(
            KeyParameter(decode(key)),
            decode(encryptedPayload.iv)
        )
        aes.init(false, ivAndKey)

        val encryptedData = decode(encryptedPayload.data)
        val minSize = aes.getOutputSize(encryptedData.size)
        val outBuf = ByteArray(minSize)
        var len = aes.processBytes(encryptedData, 0, encryptedData.size, outBuf, 0)
        len += aes.doFinal(outBuf, len)

        return outBuf.copyOf(len).toMethodCall()
    }

    override fun prepare(data: Session.MethodCall, key: String): String {
        val bytesData = data.toBytes()
        val hexKey = decode(key)
        val iv = createRandomBytes(16)

        val padding = PKCS7Padding()
        val aes = PaddedBufferedBlockCipher(
            CBCBlockCipher(AESEngine()),
            padding
        )
        aes.init(true, ParametersWithIV(KeyParameter(hexKey), iv))

        val minSize = aes.getOutputSize(bytesData.size)
        val outBuf = ByteArray(minSize)
        val length1 = aes.processBytes(bytesData, 0, bytesData.size, outBuf, 0)
        aes.doFinal(outBuf, length1)


        val hmac = HMac(SHA256Digest())
        hmac.init(KeyParameter(hexKey))

        val hmacResult = ByteArray(hmac.macSize)
        hmac.update(outBuf, 0, outBuf.size)
        hmac.update(iv, 0, iv.size)
        hmac.doFinal(hmacResult, 0)

        return payloadAdapter.toJson(
            EncryptedPayload(
                outBuf.toNoPrefixHexString(),
                hmac = hmacResult.toNoPrefixHexString(),
                iv = iv.toNoPrefixHexString()
            )
        )
    }

    /**
     * Convert FROM request bytes
     */
    private fun ByteArray.toMethodCall(): Session.MethodCall =
        String(this).let { json ->
            mapAdapter.fromJson(json)?.let {
                try {
                    val method = it["method"]
                    when (method) {
                        "wc_sessionRequest" -> it.toSessionRequest()
                        "wc_sessionUpdate" -> it.toSessionUpdate()
                        "eth_sendTransaction" -> it.toSendTransaction()
                        "eth_sign" -> it.toSignMessage()
                        null -> it.toResponse()
                        else -> it.toCustom()
                    }
                } catch (e: Exception) {
                    throw Session.MethodCallException.InvalidRequest(it.getId(), "$json (${e.message ?: "Unknown error"})")
                }
            } ?: throw IllegalArgumentException("Invalid json")
        }

    private fun Map<String, *>.toSessionUpdate(): Session.MethodCall.SessionUpdate {
        val params = this["params"] as? List<*> ?: throw IllegalArgumentException("params missing")
        val data = params.firstOrNull() as? Map<String, *> ?: throw IllegalArgumentException("Invalid params")
        return Session.MethodCall.SessionUpdate(
            getId(),
            data.extractSessionParams()
        )
    }

    private fun Map<String, *>.toSendTransaction(): Session.MethodCall.SendTransaction {
        val params = this["params"] as? List<*> ?: throw IllegalArgumentException("params missing")
        val data = params.firstOrNull() as? Map<*, *> ?: throw IllegalArgumentException("Invalid params")
        val from = data["from"] as? String ?: throw IllegalArgumentException("from key missing")
        val to = data["to"] as? String ?: throw IllegalArgumentException("to key missing")
        val nonce = data["nonce"] as? String ?: (data["nonce"] as? Double)?.toLong()?.toString()
        val gasPrice = data["gasPrice"] as? String
        // "gasLimit" was used in older versions of the library, kept here as a fallback for compatibility
        val gasLimit = data["gas"] as? String ?: data["gasLimit"] as? String
        val value = data["value"] as? String ?: "0x0"
        val txData = data["data"] as? String ?: throw IllegalArgumentException("data key missing")
        return Session.MethodCall.SendTransaction(getId(), from, to, nonce, gasPrice, gasLimit, value, txData)
    }

    private fun Map<String, *>.toSignMessage(): Session.MethodCall.SignMessage {
        val params = this["params"] as? List<*> ?: throw IllegalArgumentException("params missing")
        val address = params.getOrNull(0) as? String ?: throw IllegalArgumentException("Missing address")
        val message = params.getOrNull(1) as? String ?: throw IllegalArgumentException("Missing message")
        return Session.MethodCall.SignMessage(getId(), address, message)
    }

    private fun Map<String, *>.toCustom(): Session.MethodCall.Custom {
        val method = this["method"] as? String ?: throw IllegalArgumentException("method missing")
        val params = this["params"] as? List<*>
        return Session.MethodCall.Custom(getId(), method, params)
    }

    private fun Map<String, *>.toResponse(): Session.MethodCall.Response {
        val result = this["result"]
        val error = this["error"] as? Map<*, *>
        if (result == null && error == null) throw IllegalArgumentException("no result or error")
        return Session.MethodCall.Response(
            getId(),
            result,
            error?.extractError()
        )
    }

    /**
     * Convert INTO request bytes
     */
    private fun Session.MethodCall.toBytes() =
        mapAdapter.toJson(
            when (this) {
                is Session.MethodCall.SessionRequest -> this.toMap()
                is Session.MethodCall.Response -> this.toMap()
                is Session.MethodCall.SessionUpdate -> this.toMap()
                is Session.MethodCall.SendTransaction -> this.toMap()
                is Session.MethodCall.SignMessage -> this.toMap()
                is Session.MethodCall.Custom -> this.toMap()
            }
        ).toByteArray()

    private fun Session.MethodCall.SessionRequest.toMap() =
        jsonRpc(id, "wc_sessionRequest", peer.intoMap())

    private fun Session.MethodCall.SessionUpdate.toMap() =
        jsonRpc(id, "wc_sessionUpdate", params.intoMap())

    private fun Session.MethodCall.SendTransaction.toMap() =
        jsonRpc(
            id, "eth_sendTransaction", mapOf(
                "from" to from,
                "to" to to,
                "nonce" to nonce,
                "gasPrice" to gasPrice,
                "gas" to gasLimit,
                "value" to value,
                "data" to data
            )
        )

    private fun Session.MethodCall.SignMessage.toMap() =
        jsonRpc(
            id, "eth_sign", address, message
        )

    private fun Session.MethodCall.Response.toMap() =
        mutableMapOf<String, Any>(
            "id" to id,
            "jsonrpc" to "2.0"
        ).apply {
            result?.let { this["result"] = result }
            error?.let { this["error"] = error.intoMap() }
        }

    private fun Session.MethodCall.Custom.toMap() =
        jsonRpcWithList(
            id, method, params ?: emptyList<Any>()
        )

    private fun jsonRpc(id: Long, method: String, vararg params: Any) =
        jsonRpcWithList(id, method, params.asList())

    private fun jsonRpcWithList(id: Long, method: String, params: List<*>) =
        mapOf(
            "id" to id,
            "jsonrpc" to "2.0",
            "method" to method,
            "params" to params
        )

    // TODO: @JsonClass(generateAdapter = true)
    data class EncryptedPayload(
        @Json(name = "data") val data: String,
        @Json(name = "iv") val iv: String,
        @Json(name = "hmac") val hmac: String
    )
}
