package dev.kord.voice.encryption

import com.iwebpp.crypto.*
import dev.kord.voice.io.MutableByteArrayCursor
import dev.kord.voice.io.mutableCursor

@Deprecated(
    "XSalsa20 Poly1305 encryption is deprecated for Discord voice connections and will be discontinued as of " +
        "November 18th, 2024. As of this date, the voice gateway will not allow you to connect with one of the " +
        "deprecated encryption modes. The deprecation level will be raised to ERROR in 0.16.0, to HIDDEN in 0.17.0, " +
        "and this class will be removed in 0.18.0.",
    level = DeprecationLevel.WARNING,
)
public class XSalsa20Poly1305Codec(public val key: ByteArray) {
    private val encryption = XSalsa20Poly1305Encryption(key)

    public fun encrypt(
        message: ByteArray,
        mOffset: Int = 0,
        mLength: Int = message.size,
        nonce: ByteArray,
        output: MutableByteArrayCursor
    ): Boolean =
        encryption.box(message, mOffset, mLength, nonce, output)

    public fun decrypt(
        box: ByteArray,
        boxOffset: Int = 0,
        boxLength: Int = box.size,
        nonce: ByteArray,
        output: MutableByteArrayCursor
    ): Boolean =
        encryption.open(box, boxOffset, boxLength, nonce, output)
}

@Suppress("DEPRECATION")
@Deprecated(
    "XSalsa20 Poly1305 encryption is deprecated for Discord voice connections and will be discontinued as of " +
        "November 18th, 2024. As of this date, the voice gateway will not allow you to connect with one of the " +
        "deprecated encryption modes. The deprecation level will be raised to ERROR in 0.16.0, to HIDDEN in 0.17.0, " +
        "and this function will be removed in 0.18.0.",
    level = DeprecationLevel.WARNING,
)
public fun XSalsa20Poly1305Codec.encrypt(
    message: ByteArray,
    mOffset: Int = 0,
    mLength: Int = message.size,
    nonce: ByteArray
): ByteArray? {
    val buffer = ByteArray(mLength + TweetNaclFast.SecretBox.boxzerobytesLength)
    if (!encrypt(message, mOffset, mLength, nonce, buffer.mutableCursor())) return null
    return buffer
}

@Suppress("DEPRECATION")
@Deprecated(
    "XSalsa20 Poly1305 encryption is deprecated for Discord voice connections and will be discontinued as of " +
        "November 18th, 2024. As of this date, the voice gateway will not allow you to connect with one of the " +
        "deprecated encryption modes. The deprecation level will be raised to ERROR in 0.16.0, to HIDDEN in 0.17.0, " +
        "and this function will be removed in 0.18.0.",
    level = DeprecationLevel.WARNING,
)
public fun XSalsa20Poly1305Codec.decrypt(
    box: ByteArray,
    boxOffset: Int = 0,
    boxLength: Int = box.size,
    nonce: ByteArray
): ByteArray? {
    val buffer = ByteArray(boxLength - TweetNaclFast.SecretBox.boxzerobytesLength)
    if (!decrypt(box, boxOffset, boxLength, nonce, buffer.mutableCursor())) return null
    return buffer
}