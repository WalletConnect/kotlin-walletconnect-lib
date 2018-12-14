package org.walletconnect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EncryptionTest {

    @Test
    fun randomBytesHaveCorrectSize() {
        assertThat(createRandomBytes(32).size).isEqualTo(32)
        assertThat(createRandomBytes(16).size).isEqualTo(16)
    }

    @Test
    fun roundTripTest() {
        val key = createRandomBytes(32)
        listOf("probe1", "probe2", "32845si goiudeanweibutpu").forEach {
            assertThat(String(it.toByteArray().encrypt(key).decrypt(key))).isEqualTo(it)
        }
    }
}