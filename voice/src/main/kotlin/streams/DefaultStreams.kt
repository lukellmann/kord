package dev.kord.voice.streams

import com.iwebpp.crypto.*
import dev.kord.common.annotation.KordVoice
import dev.kord.common.entity.Snowflake
import dev.kord.voice.AudioFrame
import dev.kord.voice.EncryptionMode
import dev.kord.voice.encryption.AeadAes256GcmVoicePacketDecryptor
import dev.kord.voice.encryption.Decrypt
import dev.kord.voice.encryption.*
import dev.kord.voice.encryption.strategies.*
import dev.kord.voice.gateway.Speaking
import dev.kord.voice.gateway.VoiceGateway
import dev.kord.voice.io.*
import dev.kord.voice.udp.DecryptedVoicePacket
import dev.kord.voice.udp.PayloadType
import dev.kord.voice.udp.RTPPacket
import dev.kord.voice.udp.VoiceUdpSocket
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.network.sockets.*
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.writeUInt
import kotlinx.io.writeUShort

private val defaultStreamsLogger = KotlinLogging.logger { }

@KordVoice
public class DefaultStreams : Streams {

    private val voiceGateway: VoiceGateway
    private val udp: VoiceUdpSocket

    private val nonceStrategy: @Suppress("DEPRECATION") NonceStrategy?

    public constructor(voiceGateway: VoiceGateway, udp: VoiceUdpSocket) {
        this.voiceGateway = voiceGateway
        this.udp = udp
        this.nonceStrategy = null
    }

    @Deprecated(
        "XSalsa20 Poly1305 encryption is deprecated for Discord voice connections and will be discontinued as of " +
            "November 18th, 2024. As of this date, the voice gateway will not allow you to connect with one of the " +
            "deprecated encryption modes. The deprecation level will be raised to ERROR in 0.16.0, to HIDDEN in " +
            "0.17.0, and this constructor will be removed in 0.18.0.",
        ReplaceWith("DefaultStreams(voiceGateway, udp)", imports = ["dev.kord.voice.streams.DefaultStreams"]),
        level = DeprecationLevel.WARNING,
    )
    public constructor(
        voiceGateway: VoiceGateway, udp: VoiceUdpSocket, nonceStrategy: @Suppress("DEPRECATION") NonceStrategy,
    ) {
        this.voiceGateway = voiceGateway
        this.udp = udp
        this.nonceStrategy = nonceStrategy
    }

    private fun CoroutineScope.listenForUserFrames() {
        voiceGateway.events
            .filterIsInstance<Speaking>()
            .buffer(Channel.UNLIMITED)
            .onEach { speaking ->
                _ssrcToUser.update {
                    it.computeIfAbsent(speaking.ssrc) {
                        incomingAudioFrames
                            .filter { (ssrc, _) -> speaking.ssrc == ssrc }
                            .map { (_, frame) -> speaking.userId to frame }
                            .onEach { value -> _incomingUserAudioFrames.emit(value) }
                            .launchIn(this)

                        speaking.userId
                    }

                    it
                }
            }.launchIn(this)
    }

    override suspend fun listen(key: ByteArray, server: SocketAddress, encryptionMode: EncryptionMode) {
        val decryptionDelegate = @Suppress("DEPRECATION") when (encryptionMode) {
            EncryptionMode.AeadAes256GcmRtpSize -> NewDecryptionDelegate(AeadAes256GcmVoicePacketDecryptor(key))
            EncryptionMode.AeadXChaCha20Poly1305RtpSize -> TODO()
            EncryptionMode.XSalsa20Poly1305 -> LegacyDecryptionDelegate(key, NormalNonceStrategy())
            EncryptionMode.XSalsa20Poly1305Lite -> LegacyDecryptionDelegate(key, LiteNonceStrategy())
            EncryptionMode.XSalsa20Poly1305Suffix -> LegacyDecryptionDelegate(key, SuffixNonceStrategy())
            is EncryptionMode.Unknown -> throw UnsupportedOperationException("Unknown encryption mode $encryptionMode")
        }
        listen(decryptionDelegate, server)
    }

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
    override suspend fun listen(key: ByteArray, server: SocketAddress) {
        val strategy = nonceStrategy
            ?: throw UnsupportedOperationException("This DefaultStreams was created without a nonceStrategy.")
        listen(LegacyDecryptionDelegate(key, strategy), server)
    }

    private suspend fun listen(delegate: DecryptionDelegate, server: SocketAddress): Unit = coroutineScope {
        delegate.listenForIncoming(scope = this, udp, server, _incomingAudioPackets, _incomingVoicePackets)
        listenForUserFrames()
    }

    private val _incomingAudioPackets: MutableSharedFlow<RTPPacket> = MutableSharedFlow()

    override val incomingAudioPackets: SharedFlow<RTPPacket> = _incomingAudioPackets

    private val _incomingVoicePackets = MutableSharedFlow<DecryptedVoicePacket>()
    override val incomingVoicePackets: SharedFlow<DecryptedVoicePacket> get() = _incomingVoicePackets

    override val incomingAudioFrames: Flow<Pair<UInt, AudioFrame>>
        get() = incomingAudioPackets.map { it.ssrc to AudioFrame(it.payload.toByteArray()) }

    private val _incomingUserAudioFrames: MutableSharedFlow<Pair<Snowflake, AudioFrame>> =
        MutableSharedFlow()

    override val incomingUserStreams: SharedFlow<Pair<Snowflake, AudioFrame>> =
        _incomingUserAudioFrames

    private val _ssrcToUser: AtomicRef<MutableMap<UInt, Snowflake>> =
        atomic(mutableMapOf())

    override val ssrcToUser: Map<UInt, Snowflake> get() = _ssrcToUser.value
}

private interface DecryptionDelegate {
    fun listenForIncoming(
        scope: CoroutineScope,
        udp: VoiceUdpSocket,
        server: SocketAddress,
        audioPackets: MutableSharedFlow<RTPPacket>,
        voicePackets: MutableSharedFlow<DecryptedVoicePacket>,
    )
}

private class NewDecryptionDelegate(private val decrypt: Decrypt) : DecryptionDelegate {
    override fun listenForIncoming(
        scope: CoroutineScope,
        udp: VoiceUdpSocket,
        server: SocketAddress,
        audioPackets: MutableSharedFlow<RTPPacket>,
        voicePackets: MutableSharedFlow<DecryptedVoicePacket>,
    ) {
        scope.launch {
            udp.incoming.collect { datagram ->
                if (datagram.address != server) {
                    return@collect
                }
                val voicePacket = decrypt.decrypt(datagram.packet) ?: return@collect
                voicePackets.emit(voicePacket)

                val decryptedAudio = voicePacket.decryptedAudio
                @OptIn(ExperimentalUnsignedTypes::class)
                if (decryptedAudio is Buffer && audioPackets.subscriptionCount.value > 0) {
                    val buffer = Buffer()
                    val extension = voicePacket.headerExtension
                    val extensionSize = if (extension != null) {
                        buffer.writeUShort(extension.definedByProfile)
                        val extensionSize = extension.headerExtension.size
                        buffer.writeShort(extensionSize.toShort())
                        extension.headerExtension.forEach(buffer::writeUInt)
                        extensionSize
                    } else {
                        0
                    }
                    decryptedAudio.copyTo(buffer)
                    val data = buffer.readByteArray()
                    audioPackets.emit(
                        RTPPacket(
                            paddingBytes = 0u, // TODO explain
                            payloadType = PayloadType.Audio.raw, // TODO explain
                            sequence = voicePacket.sequenceNumber,
                            timestamp = voicePacket.timestamp,
                            ssrc = voicePacket.ssrc,
                            csrcIdentifiers = voicePacket.csrcs.copyOf(),
                            hasMarker = false, // TODO explain
                            hasExtension = extension != null,
                            // TODO explain
                            payload = ByteArrayView.from(data, start = extensionSize, end = data.size)!!,
                        )
                    )
                }
            }
        }
    }
}

private class LegacyDecryptionDelegate(
    private val key: ByteArray,
    private val nonceStrategy: @Suppress("DEPRECATION") NonceStrategy,
) : DecryptionDelegate {
    override fun listenForIncoming(
        scope: CoroutineScope,
        udp: VoiceUdpSocket,
        server: SocketAddress,
        audioPackets: MutableSharedFlow<RTPPacket>,
        voicePackets: MutableSharedFlow<DecryptedVoicePacket>,
    ) {
        udp.incoming
            .filter { it.address == server }
            .mapNotNull { RTPPacket.fromPacket(it.packet) }
            .filter { it.payloadType == PayloadType.Audio.raw }
            .decrypt(nonceStrategy, key)
            .clean()
            .onEach {
                audioPackets.emit(it)
                if (voicePackets.subscriptionCount.value > 0) {
                    voicePackets.emit(
                        @OptIn(ExperimentalUnsignedTypes::class)
                        DecryptedVoicePacket(
                            sequenceNumber = it.sequence,
                            timestamp = it.timestamp,
                            ssrc = it.ssrc,
                            csrcs = it.csrcIdentifiers.copyOf(),
                            headerExtension = null,
                            decryptedAudio = Buffer().apply {
                                val payload = it.payload
                                write(payload.data, startIndex = payload.dataStart, endIndex = payload.dataEnd)
                            },
                        )
                    )
                }
            }
            .launchIn(scope)
    }
}

@Suppress("DEPRECATION")
private fun Flow<RTPPacket>.decrypt(nonceStrategy: NonceStrategy, key: ByteArray): Flow<RTPPacket> {
    val codec = XSalsa20Poly1305Codec(key)
    val nonceBuffer = ByteArray(TweetNaclFast.SecretBox.nonceLength).mutableCursor()

    val decryptedBuffer = ByteArray(512)
    val decryptedCursor = decryptedBuffer.mutableCursor()
    val decryptedView = decryptedBuffer.view()

    return mapNotNull {
        nonceBuffer.reset()
        decryptedCursor.reset()

        nonceBuffer.writeByteView(nonceStrategy.strip(it))

        val decrypted = with(it.payload) {
            codec.decrypt(data, dataStart, viewSize, nonceBuffer.data, decryptedCursor)
        }

        if (!decrypted) {
            defaultStreamsLogger.trace { "failed to decrypt the packet with data ${it.payload.data.contentToString()} at offset ${it.payload.dataStart} and length ${it.payload.viewSize - 4}" }
            return@mapNotNull null
        }

        decryptedView.resize(0, decryptedCursor.cursor)

        // mutate the payload data and update the view
        it.payload.data.mutableCursor().writeByteViewOrResize(decryptedView)
        it.payload.resize(0, decryptedView.viewSize)

        it
    }
}

private fun Flow<RTPPacket>.clean(): Flow<RTPPacket> {
    fun processExtensionHeader(payload: ByteArrayView) = with(payload.readableCursor()) {
        consume(Short.SIZE_BYTES) // profile, ignore it
        val countOf32BitWords = readShort() // amount of extension header "words"
        consume((countOf32BitWords * 32) / Byte.SIZE_BITS) // consume extension header

        payload.resize(start = cursor)
    }

    return map { packet ->
        if (packet.hasExtension)
            processExtensionHeader(packet.payload)

        packet
    }
}
