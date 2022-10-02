package org.walletconnect

import com.squareup.moshi.Moshi
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.walletconnect.impls.MoshiPayloadAdapter


class TheWCSession {

    @Test
    fun canParseSessionUpdate() {
        val adapter = MoshiPayloadAdapter(Moshi.Builder().build())
        with(adapter) {
            val json = """{
                |"id":42,
                |"result":{},
                |"method":"wc_sessionUpdate",
                |"params":[{
                |  "approved":true,
                |  "chainId":420,
                |  "accounts":["0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045","0xb8c2c29ee19d8307cb7255e1cd9cbde883a267d5"]
                |}]
                |}""".trimMargin()
            val tested = json.toByteArray().toMethodCall()
            assertThat(tested).isInstanceOf(Session.MethodCall.SessionUpdate::class.java)
            assertThat(tested.id()).isEqualTo(42)
            val testedAndTyped = tested as Session.MethodCall.SessionUpdate

            assertThat(testedAndTyped.params.approved).isTrue
            assertThat(testedAndTyped.params.accounts).isEqualTo(
                listOf("0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045", "0xb8c2c29ee19d8307cb7255e1cd9cbde883a267d5")
            )
            assertThat(testedAndTyped.params.chainId).isEqualTo(420)
        }

    }
}

