package dev.kord.voice.encryption.strategies

import dev.kord.voice.io.ByteArrayView
import dev.kord.voice.io.MutableByteArrayCursor
import dev.kord.voice.io.view
import dev.kord.voice.udp.RTPPacket
import kotlin.random.Random

private const val SUFFIX_NONCE_LENGTH = 24

@Deprecated(
    "XSalsa20 Poly1305 encryption is deprecated for Discord voice connections and will be discontinued as of " +
        "November 18th, 2024. As of this date, the voice gateway will not allow you to connect with one of the " +
        "deprecated encryption modes. The deprecation level will be raised to ERROR in 0.16.0, to HIDDEN in 0.17.0, " +
        "and this class will be removed in 0.18.0.",
    level = DeprecationLevel.WARNING,
)
public class SuffixNonceStrategy : @Suppress("DEPRECATION") NonceStrategy {
    override val nonceLength: Int = SUFFIX_NONCE_LENGTH

    private val nonceBuffer: ByteArray = ByteArray(SUFFIX_NONCE_LENGTH)
    private val nonceView = nonceBuffer.view()

    override fun strip(packet: RTPPacket): ByteArrayView {
        return with(packet.payload) {
            val nonce = view(dataEnd - SUFFIX_NONCE_LENGTH, dataEnd)!!
            resize(dataStart, dataEnd - SUFFIX_NONCE_LENGTH)
            nonce
        }
    }

    override fun generate(header: () -> ByteArrayView): ByteArrayView {
        Random.Default.nextBytes(nonceBuffer)
        return nonceView
    }

    override fun append(nonce: ByteArrayView, cursor: MutableByteArrayCursor) {
        cursor.writeByteView(nonce)
    }
}