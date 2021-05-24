package io.walletconnect.example.server

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.lang.Exception
import java.lang.ref.WeakReference
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class BridgeServer(moshi: Moshi) : WebSocketServer(InetSocketAddress(PORT)) {

    private val adapter = moshi.adapter<Map<String, Any>>(
            Types.newParameterizedType(
                    Map::class.java,
                    String::class.java,
                    Any::class.java
            )
    )

    private val pubs: MutableMap<String, MutableList<WeakReference<WebSocket>>> = ConcurrentHashMap()
    private val pubsLock = Any()
    private val pubsCache: MutableMap<String, String?> = ConcurrentHashMap()

    override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
        Log.d("#####", "onOpen: ${conn?.remoteSocketAddress?.address?.hostAddress}")
    }

    override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
        Log.d("#####", "onClose: ${conn?.remoteSocketAddress?.address?.hostAddress}")
        conn?.let { cleanUpSocket(it) }
    }

    override fun onMessage(conn: WebSocket?, message: String?) {
        Log.d("#####", "Message: $message")
        try {
            conn ?: error("Unknown socket")
            message?.also {
                val msg = adapter.fromJson(it) ?: error("Invalid message")
                val type: String = msg["type"] as String? ?: error("Type not found")
                val topic: String = msg["topic"] as String? ?: error("Topic not found")
                when (type) {
                    "pub" -> {
                        var sendMessage = false
                        pubs[topic]?.forEach { r ->
                            r.get()?.apply {
                                send(message)
                                sendMessage = true
                            }
                        }
                        if (!sendMessage) {
                            Log.d("#####", "Cache message: $message")
                            pubsCache[topic] = message
                        }
                    }
                    "sub" -> {
                        pubs.getOrPut(topic, { mutableListOf() }).add(WeakReference(conn))
                        pubsCache[topic]?.let { cached ->
                            Log.d("#####", "Send cached: $cached")
                            conn.send(cached)
                        }
                    }
                    else -> error("Unknown type")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStart() {
        Log.d("#####", "Server started")
        connectionLostTimeout = 0
    }

    override fun onError(conn: WebSocket?, ex: Exception?) {
        Log.d("#####", "onError")
        ex?.printStackTrace()
        conn?.let { cleanUpSocket(it) }
    }

    private fun cleanUpSocket(conn: WebSocket) {
        synchronized(pubsLock) {
            pubs.forEach {
                it.value.removeAll { r -> r.get().let { v -> v == null || v == conn } }
            }
        }
    }

    companion object {
        val PORT = 5000 + Random().nextInt(60000)
    }
}