package dev.kord.voice.udp

import dev.kord.common.annotation.KordVoice
import dev.kord.voice.AudioFrame
import dev.kord.voice.AudioProvider
import dev.kord.voice.FrameInterceptor
import dev.kord.voice.encryption.strategies.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random

private val audioFrameSenderLogger = KotlinLogging.logger { }

@KordVoice
public class DefaultAudioFrameSenderData private constructor(private val delegate: Delegate) {
    // TODO move back to DefaultAudioFrameSenderData when deprecated declarations are removed
    private data class Delegate(
        val udp: VoiceUdpSocket,
        val interceptor: FrameInterceptor,
        val provider: AudioProvider,
        val nonceStrategy: @Suppress("DEPRECATION") NonceStrategy?,
    )

    public constructor(udp: VoiceUdpSocket, interceptor: FrameInterceptor, provider: AudioProvider) :
        this(Delegate(udp, interceptor, provider, nonceStrategy = null))

    public val udp: VoiceUdpSocket get() = delegate.udp
    public val interceptor: FrameInterceptor get() = delegate.interceptor
    public val provider: AudioProvider get() = delegate.provider
    public operator fun component1(): VoiceUdpSocket = udp
    public operator fun component2(): FrameInterceptor = interceptor
    public operator fun component3(): AudioProvider = provider
    override fun equals(other: Any?): Boolean = other is DefaultAudioFrameSenderData && this.delegate == other.delegate
    override fun hashCode(): Int = delegate.hashCode()
    override fun toString(): String = when (val n = delegate.nonceStrategy) {
        null -> "DefaultAudioFrameSenderData(udp=$udp, interceptor=$interceptor, provider=$provider)"
        else -> "DefaultAudioFrameSenderData(udp=$udp, interceptor=$interceptor, provider=$provider, nonceStrategy=$n)"
    }

    public fun copy(
        udp: VoiceUdpSocket = this.udp, interceptor: FrameInterceptor = this.interceptor,
        provider: AudioProvider = this.provider,
    ): DefaultAudioFrameSenderData =
        DefaultAudioFrameSenderData(delegate.copy(udp = udp, interceptor = interceptor, provider = provider))

    @Deprecated(
        "XSalsa20 Poly1305 encryption is deprecated for Discord voice connections and will be discontinued as of " +
            "November 18th, 2024. As of this date, the voice gateway will not allow you to connect with one of the " +
            "deprecated encryption modes. The deprecation level will be raised to ERROR in 0.16.0, to HIDDEN in " +
            "0.17.0, and this constructor will be removed in 0.18.0.",
        level = DeprecationLevel.WARNING,
    )
    public constructor(
        udp: VoiceUdpSocket, interceptor: FrameInterceptor, provider: AudioProvider,
        nonceStrategy: @Suppress("DEPRECATION") NonceStrategy,
    ) : this(Delegate(udp, interceptor, provider, nonceStrategy))

    @Deprecated(
        "XSalsa20 Poly1305 encryption is deprecated for Discord voice connections and will be discontinued as of " +
            "November 18th, 2024. As of this date, the voice gateway will not allow you to connect with one of the " +
            "deprecated encryption modes. The deprecation level will be raised to ERROR in 0.16.0, to HIDDEN in " +
            "0.17.0, and this property will be removed in 0.18.0.",
        level = DeprecationLevel.WARNING,
    )
    public val nonceStrategy: @Suppress("DEPRECATION") NonceStrategy
        get() = delegate.nonceStrategy ?: throw UnsupportedOperationException(
            "This DefaultAudioFrameSenderData was created without a nonceStrategy."
        )

    @Suppress("PropertyName")
    internal val _nonceStrategy: @Suppress("DEPRECATION") NonceStrategy? get() = delegate.nonceStrategy

    @Suppress("DEPRECATION", "DeprecatedCallableAddReplaceWith")
    @Deprecated(
        "XSalsa20 Poly1305 encryption is deprecated for Discord voice connections and will be discontinued as of " +
            "November 18th, 2024. As of this date, the voice gateway will not allow you to connect with one of the " +
            "deprecated encryption modes. The deprecation level will be raised to ERROR in 0.16.0, to HIDDEN in " +
            "0.17.0, and this function will be removed in 0.18.0.",
        level = DeprecationLevel.WARNING,
    )
    public operator fun component4(): NonceStrategy = nonceStrategy

    @Deprecated(
        "XSalsa20 Poly1305 encryption is deprecated for Discord voice connections and will be discontinued as of " +
            "November 18th, 2024. As of this date, the voice gateway will not allow you to connect with one of the " +
            "deprecated encryption modes. The deprecation level will be raised to ERROR in 0.16.0, to HIDDEN in " +
            "0.17.0, and this function will be removed in 0.18.0.",
        level = DeprecationLevel.WARNING,
    )
    public fun copy(
        udp: VoiceUdpSocket = this.udp, interceptor: FrameInterceptor = this.interceptor,
        provider: AudioProvider = this.provider,
        nonceStrategy: @Suppress("DEPRECATION") NonceStrategy = NONCE_STRATEGY_SENTINEL,
    ): DefaultAudioFrameSenderData = when {
        // nonceStrategy was not overridden, keep the old one by copying the delegate without passing any nonceStrategy
        nonceStrategy === NONCE_STRATEGY_SENTINEL ->
            DefaultAudioFrameSenderData(delegate.copy(udp = udp, interceptor = interceptor, provider = provider))
        else -> DefaultAudioFrameSenderData(Delegate(udp, interceptor, provider, nonceStrategy))
    }

    private companion object {
        @Suppress("DEPRECATION") // used as a marker by comparing the identity with ===
        private val NONCE_STRATEGY_SENTINEL: NonceStrategy = SuffixNonceStrategy()
    }
}

@KordVoice
public class DefaultAudioFrameSender(
    public val data: DefaultAudioFrameSenderData
) : AudioFrameSender {
    override suspend fun start(configuration: AudioFrameSenderConfiguration): Unit = coroutineScope {
        var sequence: UShort = Random.nextBits(UShort.SIZE_BITS).toUShort()

        val packetProvider = when (val strategy = data._nonceStrategy) {
            null -> DefaultAudioPacketProvider(configuration.key, configuration.encryptionMode)
            else -> @Suppress("DEPRECATION") DefaultAudioPacketProvider(configuration.key, strategy)
        }

        val frames = Channel<AudioFrame?>(Channel.RENDEZVOUS)
        with(data.provider) { launch { provideFrames(frames) } }

        audioFrameSenderLogger.trace { "audio poller starting." }

        try {
            with(data.interceptor) {
                frames.consumeAsFlow()
                    .intercept(configuration.interceptorConfiguration)
                    .filterNotNull()
                    .map { packetProvider.provide(sequence, sequence * 960u, configuration.ssrc, it.data) }
                    .map { Datagram(ByteReadPacket(it.data, it.dataStart, it.viewSize), configuration.server) }
                    .onEach(data.udp::send)
                    .onEach { sequence++ }
                    .collect()
            }
        } catch (e: Exception) {
            audioFrameSenderLogger.trace(e) { "poller stopped with reason" }
            /* we're done polling, nothing to worry about */
        }
    }
}
