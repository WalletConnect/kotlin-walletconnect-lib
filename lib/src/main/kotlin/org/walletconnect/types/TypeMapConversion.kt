package org.walletconnect.types

import org.walletconnect.Session


fun Session.PeerData.intoMap(params: MutableMap<String, Any?> = mutableMapOf()) =
    params.also {
        params["peerId"] = this.id
        params["peerMeta"] = (this.meta?.let { meta ->
            mutableMapOf<String, Any>(
                "description" to (meta.description ?: ""),
                "url" to (meta.url ?: ""),
                "name" to (meta.name ?: "")
            ).apply {
                meta.ssl?.let { put("ssl", it) }
                meta.icons?.let { put("icons", it) }
            }
        } ?: emptyMap<String, Any>())
    }

fun Session.SessionParams.intoMap(params: MutableMap<String, Any?> = mutableMapOf()) =
    params.also {
        it["approved"] = approved
        it["chainId"] = chainId
        it["accounts"] = accounts
        it["message"] = message
    }

fun Session.Error.intoMap(params: MutableMap<String, Any?> = mutableMapOf()) =
    params.also {
        it["code"] = code
        it["message"] = message
    }
