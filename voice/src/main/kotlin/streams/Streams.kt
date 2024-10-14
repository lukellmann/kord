package dev.kord.voice.streams

import dev.kord.common.annotation.KordVoice
import dev.kord.common.entity.Snowflake
import dev.kord.voice.AudioFrame
import dev.kord.voice.EncryptionMode
import dev.kord.voice.udp.DecryptedVoicePacket
import dev.kord.voice.udp.RTPPacket
import io.ktor.network.sockets.*
import kotlinx.coroutines.flow.Flow

/**
 * A representation of receiving voice through Discord and different stages of processing.
 */
@KordVoice
public interface Streams {
    /**
     * Starts propagating packets from [server] with the following [key] to decrypt the incoming frames.
     */
    @Deprecated(
        "XSalsa20 Poly1305 encryption is deprecated for Discord voice connections and will be discontinued as of " +
            "November 18th, 2024. As of this date, the voice gateway will not allow you to connect with one of the " +
            "deprecated encryption modes. The deprecation level will be raised to ERROR in 0.16.0, to HIDDEN in " +
            "0.17.0, and this function will be removed in 0.18.0.",
        ReplaceWith(
            "this.listen(key, server, EncryptionMode.AeadXChaCha20Poly1305RtpSize)",
            imports = ["dev.kord.voice.EncryptionMode"],
        ),
        level = DeprecationLevel.WARNING,
    )
    public suspend fun listen(key: ByteArray, server: SocketAddress)

    /**
     * Starts propagating packets from [server] with the following [key] to decrypt the incoming frames according to
     * [encryptionMode].
     */
    public suspend fun listen(key: ByteArray, server: SocketAddress, encryptionMode: EncryptionMode)

    /**
     * A flow of all incoming [dev.kord.voice.udp.RTPPacket]s through the UDP connection.
     */
    public val incomingAudioPackets: Flow<RTPPacket>

    /**
     * A flow of all incoming [DecryptedVoicePacket]s through the UDP connection.
     */
    public val incomingVoicePackets: Flow<DecryptedVoicePacket>

    /**
     * A flow of all incoming [AudioFrame]s mapped to their [ssrc][UInt].
     */
    public val incomingAudioFrames: Flow<Pair<UInt, AudioFrame>>

    /**
     * A flow of all incoming [AudioFrame]s mapped to their [userId][Snowflake].
     * Streams for every user should be built over time and will not be immediately available.
     */
    public val incomingUserStreams: Flow<Pair<Snowflake, AudioFrame>>

    /**
     * A map of [ssrc][UInt]s to their corresponding [userId][Snowflake].
     */
    public val ssrcToUser: Map<UInt, Snowflake>
}
