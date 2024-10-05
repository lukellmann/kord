package dev.kord.voice.handlers

import dev.kord.voice.EncryptionMode
import dev.kord.voice.EncryptionMode.*
import dev.kord.voice.FrameInterceptorConfiguration
import dev.kord.voice.VoiceConnection
import dev.kord.voice.encryption.isAeadAes256GcmSupported
import dev.kord.voice.encryption.strategies.*
import dev.kord.voice.gateway.*
import dev.kord.voice.udp.AudioFrameSenderConfiguration
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.network.sockets.*
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

private val udpLifeCycleLogger = KotlinLogging.logger { }

internal class UdpLifeCycleHandler(
    flow: Flow<VoiceEvent>,
    private val connection: VoiceConnection
) : ConnectionEventHandler<VoiceEvent>(flow, "UdpInterceptor") {
    private var ssrc: UInt? by atomic(null)
    private var server: InetSocketAddress? by atomic(null)

    private var audioSenderJob: Job? by atomic(null)
    private var encryptionMode: EncryptionMode? by atomic(null)

    @OptIn(ExperimentalUnsignedTypes::class)
    override suspend fun start() = coroutineScope {
        on<Ready> {
            ssrc = it.ssrc
            server = InetSocketAddress(it.ip, it.port)

            val ip: InetSocketAddress = connection.socket.discoverIp(server!!, ssrc!!.toInt())

            udpLifeCycleLogger.trace { "ip discovered for voice successfully" }

            val mode = when (connection._nonceStrategy) {
                null ->
                    // prefer aead_aes256_gcm_rtpsize when available, fall back to aead_xchacha20_poly1305_rtpsize,
                    // see https://discord.com/developers/docs/topics/voice-connections#transport-encryption-modes
                    if (AeadAes256GcmRtpSize in it.modes && isAeadAes256GcmSupported) {
                        AeadAes256GcmRtpSize
                    } else {
                        AeadXChaCha20Poly1305RtpSize
                    }

                // use deprecated modes only when explicitly specified in VoiceConnection
                is @Suppress("DEPRECATION") LiteNonceStrategy -> @Suppress("DEPRECATION") XSalsa20Poly1305Lite
                is @Suppress("DEPRECATION") NormalNonceStrategy -> @Suppress("DEPRECATION") XSalsa20Poly1305
                is @Suppress("DEPRECATION") SuffixNonceStrategy -> @Suppress("DEPRECATION") XSalsa20Poly1305Suffix
            }
            encryptionMode = mode

            val selectProtocol = SelectProtocol(
                protocol = "udp",
                data = SelectProtocol.Data(
                    address = ip.hostname,
                    port = ip.port,
                    mode = mode,
                )
            )

            connection.voiceGateway.send(selectProtocol)
        }

        on<SessionDescription> {
            val mode = it.mode
            val expectedMode = encryptionMode
            check(mode == expectedMode) {
                "Session Description contained unexpected encryption mode: $mode. Specified $expectedMode in Select " +
                    "Protocol."
            }

            with(connection) {
                val config = AudioFrameSenderConfiguration(
                    ssrc = ssrc!!,
                    key = it.secretKey.toUByteArray().toByteArray(),
                    server = server!!,
                    interceptorConfiguration = FrameInterceptorConfiguration(gateway, voiceGateway, ssrc!!),
                    encryptionMode = mode,
                )

                audioSenderJob?.cancel()
                audioSenderJob = launch { frameSender.start(config) }
            }
        }

        on<Close> {
            audioSenderJob?.cancel()
            audioSenderJob = null
        }
    }
}
