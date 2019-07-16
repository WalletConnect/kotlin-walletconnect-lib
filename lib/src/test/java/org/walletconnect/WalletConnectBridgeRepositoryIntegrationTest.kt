package org.walletconnect

import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import org.junit.Test
import org.walletconnect.impls.FileWCSessionStore
import org.walletconnect.impls.MoshiPayloadAdapter
import org.walletconnect.impls.OkHttpTransport
import org.walletconnect.impls.WCSession
import java.io.File
import java.util.concurrent.TimeUnit

class WalletConnectBridgeRepositoryIntegrationTest {


    /**
     * Integration test that can be used with the wallet connect example dapp
     */
    //@Test
    fun approveSession() {
        val client = OkHttpClient.Builder().pingInterval(1000, TimeUnit.MILLISECONDS).build()
        val moshi = Moshi.Builder().build()
        val sessionDir = File("build/tmp/").apply { mkdirs() }
        val sessionStore = FileWCSessionStore(File(sessionDir, "test_store.json").apply { createNewFile() }, moshi)
        val uri =
            "wc:56f58ba1-8012-40c1-a5be-cf24b1f4884f@1?bridge=https%3A%2F%2Fbridge.walletconnect.org&key=60f6dddb470b32863dba98ba3c7d4d8965ad6dc6ac205108c873d2cbd0ba7f1e"

        val config = Session.Config.fromWCUri(uri)
        val session = WCSession(
            config,
            MoshiPayloadAdapter(moshi),
            sessionStore,
            OkHttpTransport.Builder(client, moshi),
            Session.PeerMeta(name = "WC Unit Test")
        )

        session.addCallback(object : Session.Callback {
            override fun transportStatus(status: Session.Transport.Status) {
                System.out.println("transportStatus: $status")
            }

            override fun handleMethodCall(call: Session.MethodCall) {
                System.out.println("handleMethodCall: $call")
            }

            override fun sessionApproved() {
                System.out.println("sessionApproved()")
            }

            override fun sessionClosed() {
                System.out.println("sessionClosed()")
            }

        })
        session.init()
        Thread.sleep(2000)
        session.approve(listOf("0x00000000000000000000000000000000DeaDBEAF"), 4)
        Thread.sleep(100000)
    }
}

