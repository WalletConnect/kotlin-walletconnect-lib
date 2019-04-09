package org.walletconnect.impls

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import okhttp3.*
import org.walletconnect.Session
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

class OkHttpTransport(
    val client: OkHttpClient,
    val serverUrl: String,
    val statusHandler: (Session.Transport.Status) -> Unit,
    val messageHandler: (Session.Transport.Message) -> Unit,
    moshi: Moshi
) : Session.Transport, WebSocketListener() {

    private val adapter = moshi.adapter<Map<String, String>>(
        Types.newParameterizedType(
            Map::class.java,
            String::class.java,
            String::class.java
        )
    )

    private val socketLock = Any()
    private var socket: WebSocket? = null
    private var connected: Boolean = false
    private val queue: Queue<Session.Transport.Message> = ConcurrentLinkedQueue()

    override fun status(): Session.Transport.Status =
        if (connected) Session.Transport.Status.CONNECTED else Session.Transport.Status.DISCONNECTED

    override fun connect(): Boolean {
        synchronized(socketLock) {
            socket ?: run {
                connected = false
                val bridgeWS = serverUrl.replace("https://", "wss://").replace("http://", "ws://")
                socket = client.newWebSocket(Request.Builder().url(bridgeWS).build(), this)
                return true
            }
        }
        return false
    }

    override fun send(message: Session.Transport.Message) {
        queue.offer(message)
        drainQueue()
    }

    private fun drainQueue() {
        if (connected) {
            socket?.let { s ->
                queue.poll()?.let {
                    s.send(adapter.toJson(it.toMap()))
                    drainQueue() // continue draining untie there are no more messages
                }
            }
        } else {
            connect()
        }
    }

    private fun Session.Transport.Message.toMap() =
        mapOf(
            "topic" to topic,
            "type" to type,
            "payload" to payload
        )

    override fun close() {
        socket?.close(1000, null)
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        super.onOpen(webSocket, response)
        connected = true
        drainQueue()
        statusHandler(Session.Transport.Status.CONNECTED)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        super.onMessage(webSocket, text)
        adapter.fromJson(text)?.toMessage()?.let { messageHandler(it) }
    }

    private fun Map<String, String>.toMessage(): Session.Transport.Message? {
        val topic = get("topic") ?: return null
        val type = get("type") ?: return null
        val payload = get("payload") ?: return null
        return Session.Transport.Message(topic, type, payload)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        super.onFailure(webSocket, t, response)
        disconnected()
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        super.onClosed(webSocket, code, reason)
        disconnected()
    }

    private fun disconnected() {
        socket = null
        connected = false
        statusHandler(Session.Transport.Status.DISCONNECTED)
        // TODO: maybe implement a period retry
    }

    class Builder(val client: OkHttpClient, val moshi: Moshi) :
        Session.Transport.Builder {
        override fun build(
            url: String,
            statusHandler: (Session.Transport.Status) -> Unit,
            messageHandler: (Session.Transport.Message) -> Unit
        ): Session.Transport =
            OkHttpTransport(client, url, statusHandler, messageHandler, moshi)

    }

}
