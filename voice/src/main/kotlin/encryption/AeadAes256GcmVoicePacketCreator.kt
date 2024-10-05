package dev.kord.voice.encryption

import dev.kord.voice.udp.DecryptedVoicePacket
import dev.kord.voice.udp.DecryptedVoicePacket.Companion.EMPTY_UINT_ARRAY
import dev.kord.voice.udp.RTP_HEADER_LENGTH
import io.ktor.utils.io.core.*
import java.security.Security
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Cipher.DECRYPT_MODE
import javax.crypto.Cipher.ENCRYPT_MODE
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.use

private const val AES_256_GCM = "AES_256/GCM/NoPadding"
private const val AES = "AES_256" // TODO probably only need AES

internal val isAeadAes256GcmSupported
    get() = Security.getAlgorithms("Cipher")
        .any { algorithm -> AES_256_GCM.equals(algorithm, ignoreCase = true) }

private const val AUTH_TAG_SIZE = 16
private const val AUTH_TAG_BITS = AUTH_TAG_SIZE * 8
private const val IV_SIZE = 12
private const val NONCE_SIZE = 4
private const val ADDITIONAL_SIZE = RTP_HEADER_LENGTH + AUTH_TAG_SIZE + NONCE_SIZE

internal class AeadAes256GcmVoicePacketCreator(key: ByteArray) : EncryptedVoicePacketCreator {

    // the first 4 bytes are the 32-bit incremental nonce (big endian), the remaining bytes are 0
    private val ivBuffer = ByteArray(IV_SIZE)
    private var nonce = 0
    private val cipher: Cipher = Cipher.getInstance(AES_256_GCM)
    private val key = SecretKeySpec(key, AES)

    override fun createEncryptedVoicePacket(
        sequence: UShort,
        timestamp: UInt,
        ssrc: UInt,
        audioPlaintext: ByteArray, // TODO rename to plaintextAudio?
    ): ByteArray {
        val nonce = nonce++
        val plaintextSize = audioPlaintext.size
        val packetSize = plaintextSize + ADDITIONAL_SIZE
        val packet = ByteArray(packetSize) // TODO use cipher.getOutputSize?

        // write the header into the voice packet
        packet.writeRtpHeader(sequence, timestamp, ssrc)

        // encrypt the audio into the voice packet
        ivBuffer.writeIntBigEndian(offset = 0, nonce)
        cipher.init(ENCRYPT_MODE, key, GCMParameterSpec(AUTH_TAG_BITS, ivBuffer))
        cipher.updateAAD(packet, /* offset = */ 0, /* len = */ RTP_HEADER_LENGTH)
        val written = cipher.doFinal(
            /* input = */ audioPlaintext, /* inputOffset = */ 0, /* inputLen = */ plaintextSize,
            /* output = */ packet, /* outputOffset = */ RTP_HEADER_LENGTH,
        )
        check(written == plaintextSize + AUTH_TAG_SIZE) { "Ciphertext doesn't have the expected length." }

        // append the nonce to the end of the voice packet
        packet.writeIntBigEndian(offset = packetSize - NONCE_SIZE, nonce)

        return packet
    }
}


internal class AeadAes256GcmVoicePacketDecryptor(key: ByteArray) : Decrypt() {
    private val ivBuffer = ByteArray(IV_SIZE)
    private val cipher: Cipher = Cipher.getInstance(AES_256_GCM)
    private val key = SecretKeySpec(key, AES)
    private var buffer = ByteArray(512)

    fun decrypt(audioPacket: ByteReadPacket): DecryptedVoicePacket? = audioPacket.use { packet ->
        val headerSize = readUnencryptedRtpHeaderPart(packet)
        if (headerSize < 0) {
            return null
        }
        // TODO padding handling
        val payloadSize = packet.remaining.toInt()
        if (payloadSize < NONCE_SIZE) { // todo tag size
            return null
        }
        if (buffer.size < payloadSize) {
            buffer = ByteArray(payloadSize) // todo something like max(payloadSize, doubleSize) ?
        }
        packet.readFully(buffer, offset = 0, length = payloadSize)

        // read the nonce from the end of the voice packet
        val ciphertextSize = payloadSize - NONCE_SIZE
        buffer.copyInto(ivBuffer, destinationOffset = 0, startIndex = ciphertextSize, endIndex = payloadSize)

        cipher.init(DECRYPT_MODE, key, GCMParameterSpec(AUTH_TAG_BITS, ivBuffer))
        cipher.updateAAD(unencryptedRtpHeaderPartBuffer, /* offset = */ 0, /* len = */ headerSize)

        // TODO use cipher.getOutputSize?
        val expectedPlaintextSize = ciphertextSize - AUTH_TAG_SIZE
        val written = try {
            // TODO comment about safety of same input and output buffer
            cipher.doFinal(
                /* input = */ buffer, /* inputOffset = */ 0, /* inputLen = */ ciphertextSize,
                /* output = */ buffer,
            )
        } catch (_: AEADBadTagException) {
            return null
        }
        if (written != expectedPlaintextSize) {
            return null
        }

        return createDecryptedVoicePacket(headerSize)
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun createDecryptedVoicePacket(headerSize: Int): DecryptedVoicePacket {
        val sequenceNumber = unencryptedRtpHeaderPartBuffer.readShortBigEndian(offset = 2).toUShort()
        val timestamp = unencryptedRtpHeaderPartBuffer.readIntBigEndian(offset = 4).toUInt()
        val ssrc = unencryptedRtpHeaderPartBuffer.readIntBigEndian(offset = 8).toUInt()

        val csrcCount = csrcCount
        val csrcs = if (csrcCount > 0) UIntArray(csrcCount) else EMPTY_UINT_ARRAY
        for (i in 0..<csrcCount) {
            csrcs[i] = unencryptedRtpHeaderPartBuffer
                .readIntBigEndian(offset = MIN_UNENCRYPTED_RTP_HEADER_PART_SIZE + (i * CSRC_SIZE))
                .toUInt()
        }

        val extensionLength = encryptedExtensionPartLength
        val headerExtension = if (extensionLength >= 0) {
            val definedByProfile = unencryptedRtpHeaderPartBuffer
                .readShortBigEndian(offset = headerSize - EXTENSION_PREAMBLE_SIZE)
                .toUShort()
            val headerExtension = if (extensionLength > 0) UIntArray(extensionLength) else EMPTY_UINT_ARRAY
            for (i in 0..<extensionLength) {
                headerExtension[i] = buffer.readIntBigEndian(offset = i * EXTENSION_WORD_SIZE).toUInt()
            }
            DecryptedVoicePacket.HeaderExtension(definedByProfile, headerExtension)
        } else {
            null
        }

        // TODO handle padding
        val decryptedAudio =
            buffer.copyOfRange(fromIndex = extensionLength * EXTENSION_WORD_SIZE, toIndex = buffer.size - NONCE_SIZE)

        return DecryptedVoicePacket(
            sequenceNumber = sequenceNumber,
            timestamp = timestamp,
            ssrc = ssrc,
            csrcs = csrcs,
            headerExtension = headerExtension,
            decryptedAudio = decryptedAudio,
        )
    }
}
