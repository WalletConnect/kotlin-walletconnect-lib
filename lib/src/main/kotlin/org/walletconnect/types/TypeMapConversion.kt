package org.walletconnect.types

import org.walletconnect.Session
import org.walletconnect.nullOnThrow

fun Session.PeerMeta.intoMap(params: MutableMap<String, Any?> = mutableMapOf()) =
        params.also {
            params["peerMeta"] =
                    mutableMapOf<String, Any>(
                            "description" to (description ?: ""),
                            "url" to (url ?: ""),
                            "name" to (name ?: "")
                    ).apply {
                        icons?.let { put("icons", it) }
                    }
        }

fun Map<*, *>?.extractPeerMeta(): Session.PeerMeta {
    val description = this?.get("description") as? String
    val url = this?.get("url") as? String
    val name = this?.get("name") as? String
    val icons = nullOnThrow { (this?.get("icons") as? List<*>)?.toStringList() }
    return Session.PeerMeta(url, name, description, icons)
}


fun Session.PeerData.intoMap(params: MutableMap<String, Any?> = mutableMapOf()) =
        params.also {
            params["peerId"] = this.id
            this.meta?.intoMap(params)
        }

fun Map<*, *>.extractPeerData(): Session.PeerData {
    val peerId = this["peerId"] as? String ?: throw IllegalArgumentException("peerId missing")
    val peerMeta = this["peerMeta"] as? Map<*, *>
    return Session.PeerData(peerId, peerMeta.extractPeerMeta())
}

fun Session.SessionParams.intoMap(params: MutableMap<String, Any?> = mutableMapOf()) =
        params.also {
            it["approved"] = approved
            it["chainId"] = chainId
            it["accounts"] = accounts
            this.peerData?.intoMap(params)
        }

fun Map<String, *>.extractSessionParams(): Session.SessionParams {
    val approved = this["approved"] as? Boolean ?: throw IllegalArgumentException("approved missing")
    val chainId = (this["chainId"] as? Double)?.toLong()
    val accounts = nullOnThrow { (this["accounts"] as? List<*>)?.toStringList() }

    return Session.SessionParams(approved, chainId, accounts, nullOnThrow { this.extractPeerData() })
}

fun Map<String, *>.toSessionRequest(): Session.MethodCall.SessionRequest {
    val params = this["params"] as? List<*> ?: throw IllegalArgumentException("params missing")
    val data = params.firstOrNull() as? Map<*, *>
            ?: throw IllegalArgumentException("Invalid params")

    return Session.MethodCall.SessionRequest(
            getId(),
            data.extractPeerData(),
            data.getChainId()
    )
}

fun Session.Error.intoMap(params: MutableMap<String, Any?> = mutableMapOf()) =
        params.also {
            it["code"] = code
            it["message"] = message
        }

fun Map<*, *>.extractError(): Session.Error {
    val code = (this["code"] as? Double)?.toLong()
    val message = this["message"] as? String
    return Session.Error(code ?: 0, message ?: "Unknown error")
}

fun Map<String, *>.getId(): Long =
        (this["id"] as? Double)?.toLong() ?: throw IllegalArgumentException("id missing")

fun Map<*, *>.getChainId(): Long? = (this["chainId"] as? Double)?.toLong()

fun List<*>.toStringList(): List<String> =
        this.map {
            (it as? String) ?: throw IllegalArgumentException("List contains non-String values-en")
        }
