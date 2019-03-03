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
            "wc:31d92639-d7ab-452f-811e-336309ad20b0@1?bridge=https%3A%2F%2Fbridge.walletconnect.org&key=d2f410444aaac3f1410a922b520d5ca575332ae692d1d7b6678f0f5a444aaaa3"

        val config = Session.Config.fromWCUri(uri)
        val session = WCSession(
            config,
            MoshiPayloadAdapter(moshi),
            sessionStore,
            OkHttpTransport.Builder(client, moshi),
            Session.PayloadAdapter.PeerMeta(name = "WC Unit Test")
        )

        session.init()
        Thread.sleep(2000)
        session.approve(listOf("0x00000000000000000000000000000000DeaDBEAF"), 4)
        Thread.sleep(10000)
    }
}

