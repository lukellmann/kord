package dev.kord.voice.streams

import dev.kord.common.annotation.KordVoice
import dev.kord.common.entity.Snowflake
import dev.kord.voice.AudioFrame
import dev.kord.voice.EncryptionMode
import dev.kord.voice.udp.DecryptedVoicePacket
import dev.kord.voice.udp.RTPPacket
import io.ktor.network.sockets.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@KordVoice
public object NOPStreams : Streams {
    @Deprecated(
        "XSalsa20 Poly1305 encryption is deprecated for Discord voice connections and will be discontinued as of " +
            "November 18th, 2024. As of this date, the voice gateway will not allow you to connect with one of the " +
            "deprecated encryption modes. The deprecation level will be raised to ERROR in 0.16.0, to HIDDEN in " +
            "0.17.0, and this function will be removed in 0.18.0.",
        ReplaceWith(
            "this.listen(key, server, EncryptionMode.AeadXChaCha20Poly1305RtpSize)",
            imports = ["dev.kord.voice.EncryptionMode"],
        ),
        level = DeprecationLevel.WARNING
    )
    override suspend fun listen(key: ByteArray, server: SocketAddress) {}

    override suspend fun listen(key: ByteArray, server: SocketAddress, encryptionMode: EncryptionMode) {}
    override val incomingAudioPackets: Flow<RTPPacket> = flow { }
    override val incomingVoicePackets: Flow<DecryptedVoicePacket> = flow { }
    override val incomingAudioFrames: Flow<Pair<UInt, AudioFrame>> = flow { }
    override val incomingUserStreams: Flow<Pair<Snowflake, AudioFrame>> = flow { }
    override val ssrcToUser: Map<UInt, Snowflake> = emptyMap()
}