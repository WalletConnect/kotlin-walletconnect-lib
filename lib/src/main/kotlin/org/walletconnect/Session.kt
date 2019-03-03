package org.walletconnect

import java.net.URLDecoder

interface Session {
    fun init()
    fun approve(accounts: List<String>, chainId: Long)
    fun reject()
    fun update(accounts: List<String>, chainId: Long)
    fun kill()

    fun peerMeta(): PayloadAdapter.PeerMeta?
    fun approvedAccounts(): List<String>?

    fun approveRequest(id: Long, response: Any)
    fun rejectRequest(id: Long, errorCode: Long, errorMsg: String)

    fun addCallback(cb: Session.Callback)
    fun removeCallback(cb: Session.Callback)

    data class Config(
        val handshakeTopic: String,
        val bridge: String,
        val key: String,
        val protocol: String = "wc",
        val version: Int = 1
    ) {
        companion object {
            fun fromWCUri(uri: String): Config {
                val protocolSeparator = uri.indexOf(':')
                val handshakeTopicSeparator = uri.indexOf('@', startIndex = protocolSeparator)
                val versionSeparator = uri.indexOf('?')
                val protocol = uri.substring(0, protocolSeparator)
                val handshakeTopic = uri.substring(protocolSeparator + 1, handshakeTopicSeparator)
                val version = Integer.valueOf(uri.substring(handshakeTopicSeparator + 1, versionSeparator))
                val params = uri.substring(versionSeparator + 1).split("&").associate {
                    it.split("=").let { param -> param.first() to URLDecoder.decode(param[1], "UTF-8") }
                }
                val bridge = params["bridge"] ?: throw IllegalArgumentException("Missing bridge param in URI")
                val key = params["key"] ?: throw IllegalArgumentException("Missing key param in URI")
                return Config(handshakeTopic, bridge, key, protocol, version)
            }
        }
    }

    interface Callback {

        fun sendTransaction(
            id: Long,
            from: String,
            to: String,
            nonce: String?,
            gasPrice: String?,
            gasLimit: String?,
            value: String,
            data: String
        )

        fun signMessage(id: Long, address: String, message: String)

        fun sessionRequest(peer: PayloadAdapter.PeerData)

        fun sessionApproved()

        fun sessionClosed(msg: String?)
    }

    interface PayloadAdapter {
        fun parse(payload: String, key: String): MethodCall
        fun prepare(data: MethodCall, key: String): String

        sealed class MethodCall(private val internalId: Long) {
            fun id() = internalId

            data class SessionRequest(val id: Long, val peer: PeerData) : MethodCall(id)

            data class SessionUpdate(val id: Long, val params: SessionParams) : MethodCall(id)

            data class ExchangeKey(val id: Long, val nextKey: String, val peer: PeerData) : MethodCall(id)

            data class SendTransaction(
                val id: Long,
                val from: String,
                val to: String,
                val nonce: String?,
                val gasPrice: String?,
                val gasLimit: String?,
                val value: String,
                val data: String
            ) : MethodCall(id)

            data class SignMessage(val id: Long, val address: String, val message: String) : MethodCall(id)

            data class Response(val id: Long, val result: Any?, val error: Error? = null) : MethodCall(id)
        }

        data class PeerData(val id: String, val meta: PeerMeta?)
        data class PeerMeta(
            val url: String? = null,
            val name: String? = null,
            val description: String? = null,
            val icons: List<String>? = null,
            val ssl: Boolean? = null
        )

        data class SessionParams(val approved: Boolean, val chainId: Long?, val accounts: List<String>?, val message: String?)

        data class Error(val code: Long, val message: String)

    }

    interface Transport {

        fun connect(): Boolean

        fun send(message: Message)

        fun status(): Status

        fun close()

        enum class Status {
            CONNECTED,
            DISCONNECTED
        }

        data class Message(
            val topic: String,
            val type: String,
            val payload: String
        )

        interface Builder {
            fun build(
                url: String,
                statusHandler: (Session.Transport.Status) -> Unit,
                messageHandler: (Session.Transport.Message) -> Unit
            ): Transport
        }

    }

    sealed class MethodCallException(val id: Long, val code: Long, message: String) : IllegalArgumentException(message) {
        // TODO define proper error codes
        class InvalidMethod(id: Long, method: String) : MethodCallException(id, 42, "Unknown method: $method")
        class InvalidRequest(id: Long, request: String) : MethodCallException(id, 23, "Invalid request: $request")
        class InvalidAccount(id: Long, account: String) : MethodCallException(id, 3141, "Invalid account request: $account")
    }
}
