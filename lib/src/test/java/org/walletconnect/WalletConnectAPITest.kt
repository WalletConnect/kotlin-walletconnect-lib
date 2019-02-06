package org.walletconnect

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.walletconnect.model.NewSessionResponse
import org.walleth.khex.hexToByteArray

class WalletConnectAPITest {

    val testKey = "0xb7a143900f351f74c8b859b918f8889313ac60e5af4b63f0a63a660a1f692644".hexToByteArray()
    companion object {

        val server by lazy { MockWebServer() }

        @BeforeAll
        fun start() {
            server.start()
        }

        @AfterAll
        fun stop() {
            server.shutdown()
        }
    }

    @Test
    fun initSessionWorks() {

        server.enqueue(MockResponse().setBody("""{"sessionId":"foo"}"""))

        val api = WalletConnectAPI(server.url("").toString())
        val sessionResponse: NewSessionResponse? = api.initSession()

        assertThat(sessionResponse).isNotNull
        assertThat(sessionResponse!!.sessionId).isEqualTo("foo")

        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/session/new")
    }

    @Test
    fun createCallWorks() {

        server.enqueue(MockResponse().setBody("""{"callId":"bar"}"""))

        val api = WalletConnectAPI(server.url("").toString())
        val sessionResponse = api.createCall("yo", testKey, "aa")

        assertThat(sessionResponse).isNotNull
        assertThat(sessionResponse!!.callId).isEqualTo("bar")

        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/session/aa/call/new")
    }


    @Test
    fun getSessionWorks() {

        server.enqueue(MockResponse().setBody("""{"data":{"encryptionPayload":{"iv":"def6146e94d77cb3305379761a614135","data":"bccd0a05049020badfcff4e84aba0ae1978dad4392043e438f4d6a3e2f40a394540806b018bacecbb7780d8564b9a725efd379dfeb3daed428ae6218686c48be7e64240b239f8267505d8c185a00b3b3","hmac":"dde769cae2dcadcf56de623fd678f84cefacb334177d4e6f0ac9bb4ccef3869f"}}}"""))

        val api = WalletConnectAPI(server.url("").toString())

        val sessionResponse = api.getSession("yo", testKey)

        assertThat(sessionResponse).isNotNull
        assertThat(sessionResponse!!.accounts).isEqualTo(listOf("3c5c98e2c24da06b0b7337bce2b2f0cd"))

        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/session/yo")
    }

}