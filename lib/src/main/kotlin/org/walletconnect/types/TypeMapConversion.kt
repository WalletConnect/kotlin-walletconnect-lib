package org.walletconnect.types

import org.walletconnect.Session

fun Session.PeerMeta.intoMap(params: MutableMap<String, Any?> = mutableMapOf()) =
    params.also {
        params["peerMeta"] =
            mutableMapOf<String, Any>(
                "description" to (description ?: ""),
                "url" to (url ?: ""),
                "name" to (name ?: "")
            ).apply {
                ssl?.let { put("ssl", it) }
                icons?.let { put("icons", it) }
            }
    }

fun Session.PeerData.intoMap(params: MutableMap<String, Any?> = mutableMapOf()) =
    params.also {
        params["peerId"] = this.id
        this.meta?.intoMap(params)
    }

fun Session.SessionParams.intoMap(params: MutableMap<String, Any?> = mutableMapOf()) =
    params.also {
        it["approved"] = approved
        it["chainId"] = chainId
        it["accounts"] = accounts
        this.peerData?.intoMap(params)
    }

fun Session.Error.intoMap(params: MutableMap<String, Any?> = mutableMapOf()) =
    params.also {
        it["code"] = code
        it["message"] = message
    }
