package org.walletconnect
import com.squareup.moshi.Moshi
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.walletconnect.model.*

class WalletConnectAPI(
        bridgeURL: String,
        private val okhttp: OkHttpClient = OkHttpClient.Builder().build(),
        private val moshi: Moshi = Moshi.Builder().build()) {


    private val cleanBridgeURL = bridgeURL.trimEnd('/')

    private val newSessionResponseAdapter by lazy {
        moshi.adapter(NewSessionResponse::class.java)
    }

    private val sessionResponseAdapter by lazy {
        moshi.adapter(SessionResponse::class.java)
    }

    private val accountsResponseAdapter by lazy {
        moshi.adapter(AccountsResponse::class.java)
    }


    private val createCallResponseAdapter by lazy {
        moshi.adapter(CreateCallResponse::class.java)
    }

    fun initSession() = okhttp.newCall(Request.Builder().url("$cleanBridgeURL/session/new").post(RequestBody.create(null, "")).build())
            .execute()
            .body()?.use {
                newSessionResponseAdapter.fromJson(it.source())
            }


    fun getSession(sessionId: String, key: ByteArray): AccountsResponseInner? {

        return try {
            val sessionResponse = okhttp.newCall(Request.Builder().url("$cleanBridgeURL/session/$sessionId").build())
                    .execute()
                    .body()?.use {
                        sessionResponseAdapter.fromJson(it.source())
                    }
            sessionResponse?.let {
                val decrypted = sessionResponse.data.encryptionPayload.decrypt(key)
                accountsResponseAdapter.fromJson(String(decrypted))?.data
            }

        } catch (e: Exception) {
            null
        }
    }

    fun createCall(call: String, key: ByteArray, sessionId: String?): CreateCallResponse? {
        val adapter = moshi.adapter(NewSessionCallRequest::class.java)

        val json = adapter.toJson(NewSessionCallRequest("gets_removed_next_protocol_version", """{"data":$call}""".toByteArray().encrypt(key)))

        return createCallResponseAdapter.fromJson(

                okhttp.newCall(Request.Builder().url("$cleanBridgeURL/session/$sessionId/call/new").post(RequestBody.create(MediaType.parse("application-json"), json)).build())
                        .execute()
                        .body()!!.source())
    }

}