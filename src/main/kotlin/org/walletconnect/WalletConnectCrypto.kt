package org.walletconnect

import org.spongycastle.crypto.digests.SHA256Digest
import org.spongycastle.crypto.engines.AESEngine
import org.spongycastle.crypto.macs.HMac
import org.spongycastle.crypto.modes.CBCBlockCipher
import org.spongycastle.crypto.paddings.PKCS7Padding
import org.spongycastle.crypto.paddings.PaddedBufferedBlockCipher
import org.spongycastle.crypto.params.KeyParameter
import org.spongycastle.crypto.params.ParametersWithIV
import org.walletconnect.model.EncryptedData
import org.walleth.khex.hexToByteArray
import org.walleth.khex.toNoPrefixHexString
import java.security.SecureRandom

internal fun EncryptedData.decrypt(sessionKey: ByteArray): ByteArray {
    val padding = PKCS7Padding()
    val aes = PaddedBufferedBlockCipher(CBCBlockCipher(AESEngine()), padding)
    val ivAndKey = ParametersWithIV(KeyParameter(sessionKey), iv.hexToByteArray())
    aes.init(false, ivAndKey)

    val encryptedData = data.hexToByteArray()
    val minSize = aes.getOutputSize(encryptedData.size)
    val outBuf = ByteArray(minSize)
    val length1 = aes.processBytes(encryptedData, 0, encryptedData.size, outBuf, 0)
    val length2 = aes.doFinal(outBuf, length1)

    return outBuf.copyOf(length1 + length2)
}

internal fun ByteArray.encrypt(key: ByteArray): EncryptedData {
    val iv = createRandomBytes(16)

    val padding = PKCS7Padding()
    val aes = PaddedBufferedBlockCipher(CBCBlockCipher(AESEngine()), padding)
    aes.init(true, ParametersWithIV(KeyParameter(key), iv))

    val minSize = aes.getOutputSize(size)
    val outBuf = ByteArray(minSize)
    val length1 = aes.processBytes(this, 0, size, outBuf, 0)
    aes.doFinal(outBuf, length1)
    val hmac = HMac(SHA256Digest())
    hmac.init(KeyParameter(key))

    val hmacResult = ByteArray(hmac.macSize)

    hmac.update(outBuf, 0, outBuf.size)
    hmac.update(iv, 0, iv.size)
    hmac.doFinal(hmacResult, 0)

    return EncryptedData(
            outBuf.toNoPrefixHexString(),
            hmac = hmacResult.toNoPrefixHexString(),
            iv = iv.toNoPrefixHexString()
    )
}

internal fun createRandomBytes(i: Int) = ByteArray(i).also {
    SecureRandom().nextBytes(it)
}
