package dev.kord.voice.encryption.strategies

import dev.kord.voice.io.ByteArrayView
import dev.kord.voice.io.MutableByteArrayCursor
import dev.kord.voice.udp.RTPPacket

/**
 * An [encryption mode, regarding the nonce](https://discord.com/developers/docs/topics/voice-connections#establishing-a-voice-udp-connection-encryption-modes), supported by Discord.
 */
@Deprecated(
    "XSalsa20 Poly1305 encryption is deprecated for Discord voice connections and will be discontinued as of " +
        "November 18th, 2024. As of this date, the voice gateway will not allow you to connect with one of the " +
        "deprecated encryption modes. The deprecation level will be raised to ERROR in 0.16.0, to HIDDEN in 0.17.0, " +
        "and this interface will be removed in 0.18.0.",
    level = DeprecationLevel.WARNING,
)
public sealed interface NonceStrategy {
    /**
     * The amount of bytes this nonce will take up.
     */
    public val nonceLength: Int

    /**
     * Reads the nonce from this [packet] (also removes it if it resides in the payload), and returns a [ByteArrayView] of it.
     */
    public fun strip(packet: RTPPacket): ByteArrayView

    /**
     * Generates a nonce, may use the provided information.
     */
    public fun generate(header: () -> ByteArrayView): ByteArrayView

    /**
     * Writes the [nonce] to [cursor] in the correct relative position.
     */
    public fun append(nonce: ByteArrayView, cursor: MutableByteArrayCursor)
}