package dev.kord.voice.encryption.strategies

import dev.kord.voice.io.ByteArrayView
import dev.kord.voice.io.MutableByteArrayCursor
import dev.kord.voice.io.mutableCursor
import dev.kord.voice.io.view
import dev.kord.voice.udp.RTPPacket
import kotlinx.atomicfu.atomic

@Deprecated(
    "XSalsa20 Poly1305 encryption is deprecated for Discord voice connections and will be discontinued as of " +
        "November 18th, 2024. As of this date, the voice gateway will not allow you to connect with one of the " +
        "deprecated encryption modes. The deprecation level will be raised to ERROR in 0.16.0, to HIDDEN in 0.17.0, " +
        "and this class will be removed in 0.18.0.",
    level = DeprecationLevel.WARNING,
)
public class LiteNonceStrategy : @Suppress("DEPRECATION") NonceStrategy {
    override val nonceLength: Int = 4

    private var count: Int by atomic(0)
    private val nonceBuffer: ByteArray = ByteArray(4)
    private val nonceView = nonceBuffer.view()
    private val nonceCursor = nonceBuffer.mutableCursor()

    override fun strip(packet: RTPPacket): ByteArrayView {
        return with(packet.payload) {
            val nonce = view(dataEnd - 4, dataEnd)!!
            resize(dataStart, dataEnd - 4)
            nonce
        }
    }

    override fun generate(header: () -> ByteArrayView): ByteArrayView {
        count++
        nonceCursor.reset()
        nonceCursor.writeInt(count)
        return nonceView
    }

    override fun append(nonce: ByteArrayView, cursor: MutableByteArrayCursor) {
        cursor.writeByteView(nonce)
    }
}
