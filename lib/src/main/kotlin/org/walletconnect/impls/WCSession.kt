package org.walletconnect.impls

import org.walletconnect.Session
import org.walletconnect.nullOnThrow
import org.walletconnect.toHexString
import org.walletconnect.types.intoMap
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class WCSession(
    private val config: Session.Config,
    private val payloadAdapter: Session.PayloadAdapter,
    private val sessionStore: WCSessionStore,
    transportBuilder: Session.Transport.Builder,
    clientMeta: Session.PayloadAdapter.PeerMeta,
    clientId: String? = null
) : Session {

    private val keyLock = Any()

    // Persisted state
    private var nextKey: String?
    private var currentKey: String

    private var approvedAccounts: List<String>? = null
    private var handshakeId: Long? = null
    private var peerId: String? = null
    private var peerMeta: Session.PayloadAdapter.PeerMeta? = null

    private val clientData: Session.PayloadAdapter.PeerData

    // Getters
    private val encryptionKey: String
        get() = currentKey

    private val decryptionKey: String
        get() = currentKey

    // Non-persisted state
    private val transport = transportBuilder.build(config.bridge, ::handleStatus, ::handleMessage)
    private val requests: MutableMap<Long, (Session.PayloadAdapter.MethodCall.Response) -> Unit> = ConcurrentHashMap()
    private val sessionCallbacks: MutableSet<Session.Callback> = Collections.newSetFromMap(ConcurrentHashMap<Session.Callback, Boolean>())
    private val queue: Queue<QueuedMethod> = ConcurrentLinkedQueue()

    init {
        nextKey = null
        currentKey = config.key
        clientData = sessionStore.load(config.handshakeTopic)?.let {
            System.out.println("Session restored $it")
            nextKey = it.nextKey
            currentKey = it.currentKey
            approvedAccounts = it.approvedAccounts
            handshakeId = it.handshakeId
            peerId = it.peerData?.id
            peerMeta = it.peerData?.meta
            if (clientId != null && clientId != it.clientData.id)
                throw IllegalArgumentException("Provided clientId is different from stored clientId")
            it.clientData
        } ?: run {
            Session.PayloadAdapter.PeerData(clientId ?: UUID.randomUUID().toString(), clientMeta)
        }
        storeSession()
    }

    override fun addCallback(cb: Session.Callback) {
        sessionCallbacks.add(cb)
    }

    override fun removeCallback(cb: Session.Callback) {
        sessionCallbacks.remove(cb)
    }

    override fun peerMeta(): Session.PayloadAdapter.PeerMeta? = peerMeta

    override fun approvedAccounts(): List<String>? = approvedAccounts

    override fun init() {
        if (transport.connect()) {
            // Register for all messages for this client
            transport.send(
                Session.Transport.Message(
                    config.handshakeTopic, "sub", ""
                )
            )
        }
    }

    override fun approve(accounts: List<String>, chainId: Long) {
        val handshakeId = handshakeId ?: return
        approvedAccounts = accounts
        // We should not use classes in the Response, since this will not work with proguard
        val params = Session.PayloadAdapter.SessionParams(true, chainId, accounts, null).intoMap()
        send(Session.PayloadAdapter.MethodCall.Response(handshakeId, params))
        storeSession()
        sessionCallbacks.forEach { nullOnThrow { it.sessionApproved() } }
    }

    override fun update(accounts: List<String>, chainId: Long) {
        val params = Session.PayloadAdapter.SessionParams(true, chainId, accounts, null)
        send(Session.PayloadAdapter.MethodCall.SessionUpdate(createCallId(), params))
    }

    override fun reject() {
        handshakeId?.let {
            // We should not use classes in the Response, since this will not work with proguard
            val params = Session.PayloadAdapter.SessionParams(false, null, null, null).intoMap()
            send(Session.PayloadAdapter.MethodCall.Response(it, params))
        }
        endSession()
    }

    override fun approveRequest(id: Long, response: Any) {
        send(Session.PayloadAdapter.MethodCall.Response(id, response))
    }

    override fun rejectRequest(id: Long, errorCode: Long, errorMsg: String) {
        send(
            Session.PayloadAdapter.MethodCall.Response(
                id,
                result = null,
                error = Session.PayloadAdapter.Error(errorCode, errorMsg)
            )
        )
    }

    private fun handleStatus(status: Session.Transport.Status) {
        System.out.println("Status $status")
        when (status) {
            Session.Transport.Status.CONNECTED ->
                // Register for all messages for this client
                transport.send(
                    Session.Transport.Message(
                        clientData.id, "sub", ""
                    )
                )
            Session.Transport.Status.DISCONNECTED -> {
            } // noop
        }
    }

    private fun handleMessage(message: Session.Transport.Message) {
        if (message.type != "pub") return
        val data: Session.PayloadAdapter.MethodCall
        synchronized(keyLock) {
            try {
                data = payloadAdapter.parse(message.payload, decryptionKey)
            } catch (e: Exception) {
                handlePayloadError(e)
                return
            }
        }
        System.out.println("Data $data")
        when (data) {
            is Session.PayloadAdapter.MethodCall.SessionRequest -> {
                handshakeId = data.id
                peerId = data.peer.id
                peerMeta = data.peer.meta
                // exchangeKey stores the session no need to do that again
                exchangeKey()
                sessionCallbacks.forEach { nullOnThrow { it.sessionRequest(data.peer) } }
            }
            is Session.PayloadAdapter.MethodCall.SessionUpdate -> {
                if (!data.params.approved) {
                    endSession(data.params.message)
                }
                // TODO handle session update -> not important for our usecase
            }
            is Session.PayloadAdapter.MethodCall.ExchangeKey -> {
                peerId = data.peer.id
                peerMeta = data.peer.meta
                send(Session.PayloadAdapter.MethodCall.Response(data.id, true))
                // swapKeys stores the session no need to do that again
                swapKeys(data.nextKey)
                // TODO: expose peer meta update
            }
            is Session.PayloadAdapter.MethodCall.SendTransaction -> {
                if (accountCheck(data.id, data.from)) {
                    sessionCallbacks.forEach {
                        nullOnThrow {
                            it.sendTransaction(data.id, data.from, data.to, data.nonce, data.gasPrice, data.gasLimit, data.value, data.data)
                        }
                    }
                }
            }
            is Session.PayloadAdapter.MethodCall.SignMessage -> {
                if (accountCheck(data.id, data.address)) {
                    sessionCallbacks.forEach {
                        nullOnThrow { it.signMessage(data.id, data.address, data.message) }
                    }
                }
            }
            is Session.PayloadAdapter.MethodCall.Response -> {
                val callback = requests[data.id] ?: return
                System.out.println("Trigger callback")
                callback(data)
            }
        }
    }

    private fun accountCheck(id: Long, address: String): Boolean {
        approvedAccounts?.find { it.toLowerCase() == address.toLowerCase() } ?: run {
            handlePayloadError(Session.MethodCallException.InvalidAccount(id, address))
            return false
        }
        return true
    }

    private fun handlePayloadError(e: Exception) {
        System.out.println("Payload error $e")
        e.printStackTrace()
        (e as? Session.MethodCallException)?.let {
            rejectRequest(it.id, it.code, it.message ?: "Unknown error")
        }
    }

    private fun endSession(message: String? = null) {
        sessionStore.remove(config.handshakeTopic)
        approvedAccounts = null
        internalClose()
        sessionCallbacks.forEach { nullOnThrow { it.sessionClosed(message) } }
    }

    private fun storeSession() {
        sessionStore.store(
            config.handshakeTopic,
            WCSessionStore.State(
                config,
                clientData,
                peerId?.let { Session.PayloadAdapter.PeerData(it, peerMeta) },
                handshakeId,
                currentKey,
                nextKey,
                approvedAccounts
            )
        )
    }

    private fun generateKey(length: Int = 256) = ByteArray(length / 8).also { SecureRandom().nextBytes(it) }.toHexString()

    private fun exchangeKey() {
        val nextKey = generateKey()
        System.out.println("Key $nextKey")
        synchronized(keyLock) {
            this.nextKey = nextKey
            send(
                Session.PayloadAdapter.MethodCall.ExchangeKey(
                    createCallId(),
                    nextKey,
                    clientData
                ),
                forceSend = true // This is an exchange key ... we should force it
            ) {
                if (it.result as? Boolean == true) {
                    System.out.println("Swap Keys")
                    swapKeys()
                } else {
                    this.nextKey = null
                    drainQueue()
                }
            }
        }
        storeSession()
    }

    private fun swapKeys(newKey: String? = nextKey) {
        synchronized(keyLock) {
            newKey?.let {
                this.currentKey = it
                // We always reset the nextKey
                nextKey = null
            }
        }
        storeSession()
        drainQueue()
    }

    private fun drainQueue() {
        var method = queue.poll()
        while (method != null) {
            // We could not send it ... bail
            if (!send(method.call, method.topic, false, method.callback)) return
            method = queue.poll()
        }
    }

    // Returns true if method call was handed over to transport
    private fun send(
        msg: Session.PayloadAdapter.MethodCall,
        topic: String? = peerId,
        forceSend: Boolean = false,
        callback: ((Session.PayloadAdapter.MethodCall.Response) -> Unit)? = null
    ): Boolean {
        topic ?: return false
        // Check if key exchange is in progress
        if (!forceSend && nextKey != null) {
            queue.offer(QueuedMethod(topic, msg, callback))
            return false
        }

        val payload: String
        synchronized(keyLock) {
            payload = payloadAdapter.prepare(msg, encryptionKey)
        }
        callback?.let {
            requests[msg.id()] = callback
        }
        System.out.println("Send Request $msg")
        transport.send(Session.Transport.Message(topic, "pub", payload))
        return true
    }

    private fun createCallId() = System.currentTimeMillis() * 1000 + Random().nextInt(999)

    private fun internalClose() {
        transport.close()
    }

    override fun kill() {
        reject()
    }

    private data class QueuedMethod(
        val topic: String,
        val call: Session.PayloadAdapter.MethodCall,
        val callback: ((Session.PayloadAdapter.MethodCall.Response) -> Unit)?
    )
}

interface WCSessionStore {
    fun load(id: String): State?

    fun store(id: String, state: State)

    fun remove(id: String)

    fun list(): List<State>

    data class State(
        val config: Session.Config,
        val clientData: Session.PayloadAdapter.PeerData,
        val peerData: Session.PayloadAdapter.PeerData?,
        val handshakeId: Long?,
        val currentKey: String,
        val nextKey: String?,
        val approvedAccounts: List<String>?
    )
}
