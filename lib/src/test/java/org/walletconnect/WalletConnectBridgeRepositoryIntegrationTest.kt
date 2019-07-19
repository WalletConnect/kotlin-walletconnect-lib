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
            "wc:ffd70e47-8634-4eba-95e9-81d7d1ee3bc3@1?bridge=https%3A%2F%2Fbridge.walletconnect.org&key=10d842ec755f67ed37de894811d2b641e1e752f3a91cec05d64ed4b7735cb8c3"

        val config = Session.Config.fromWCUri(uri)
        val session = WCSession(
            config,
            MoshiPayloadAdapter(moshi),
            sessionStore,
            OkHttpTransport.Builder(client, moshi),
            Session.PeerMeta(name = "WC Unit Test")
        )

        session.addCallback(object : Session.Callback {
            override fun onStatus(status: Session.Status) {
                System.out.println("onStatus: $status")
            }

            override fun onMethodCall(call: Session.MethodCall) {
                System.out.println("onMethodCall: $call")
            }
        })
        session.init()
        Thread.sleep(2000)
        session.approve(listOf("0x00000000000000000000000000000000DeaDBEAF"), 4)
        Thread.sleep(10000)
        session.kill()
        Thread.sleep(2000)
    }
}

