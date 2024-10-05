package dev.kord.voice.encryption

import io.ktor.utils.io.core.*

internal interface EncryptedVoicePacketCreator {
    fun createEncryptedVoicePacket(sequence: UShort, timestamp: UInt, ssrc: UInt, audioPlaintext: ByteArray): ByteArray
}

private fun ByteArray.writeShortBigEndian(offset: Int, value: Short) {
    this[offset] = (value.toInt() ushr 8).toByte()
    this[offset + 1] = value.toByte()
}

internal fun ByteArray.writeIntBigEndian(offset: Int, value: Int) {
    this[offset] = (value ushr 24).toByte()
    this[offset + 1] = (value ushr 16).toByte()
    this[offset + 2] = (value ushr 8).toByte()
    this[offset + 3] = value.toByte()
}

internal fun ByteArray.readShortBigEndian(offset: Int): Short {
    return (this[offset].toInt() shl 8)
        .or(this[offset + 1].toInt() and 0xFF)
        .toShort()
}

internal fun ByteArray.readIntBigEndian(offset: Int): Int {
    return (this[offset].toInt() shl 24)
        .or((this[offset + 1].toInt() and 0xFF) shl 16)
        .or((this[offset + 2].toInt() and 0xFF) shl 8)
        .or(this[offset + 3].toInt() and 0xFF)
}

internal fun ByteArray.writeRtpHeader(sequence: UShort, timestamp: UInt, ssrc: UInt) {
    // https://discord.com/developers/docs/topics/voice-connections#transport-encryption-modes-voice-packet-structure
    // https://datatracker.ietf.org/doc/html/rfc3550#section-5.1
    this[0] = VERSION_2.toByte()
    this[1] = PAYLOAD_TYPE
    writeShortBigEndian(offset = 2, sequence.toShort())
    writeIntBigEndian(offset = 4, timestamp.toInt())
    writeIntBigEndian(offset = 8, ssrc.toInt())
}

// https://discord.com/developers/docs/topics/voice-connections#transport-encryption-modes:
// The RTP size variants determine the unencrypted size of the RTP header in the same way as SRTP, which considers CSRCs
// and (optionally) the extension preamble to be part of the unencrypted header. The deprecated variants use a fixed
// size unencrypted header for RTP.
//
// The unencrypted part of the RTP header consists of 12 bytes that are always present, up to 15 CSRCs (32 bits each)
// and an optional extension preamble (32 bits). If a header extension is present, the non-preamble part of the
// extension is encrypted together with the payload.
internal const val CSRC_SIZE = 4
internal const val EXTENSION_WORD_SIZE = 4
internal const val EXTENSION_PREAMBLE_SIZE = 4
internal const val MIN_UNENCRYPTED_RTP_HEADER_PART_SIZE = 12
private const val MAX_UNENCRYPTED_RTP_HEADER_PART_SIZE =
    MIN_UNENCRYPTED_RTP_HEADER_PART_SIZE + (15 * CSRC_SIZE) + EXTENSION_PREAMBLE_SIZE

private const val VERSION_MASK = 0b11_0_0_0000
private const val VERSION_2 = 0b10_0_0_0000 // 0x80
private const val PADDING_MASK = 0b00_1_0_0000
private const val EXTENSION_MASK = 0b00_0_1_0000
private const val CSRC_COUNT_MASK = 0b00_0_0_1111
private const val PAYLOAD_TYPE: Byte = 0x78

internal abstract class Decrypt {
    @JvmField
    protected val unencryptedRtpHeaderPartBuffer = ByteArray(MAX_UNENCRYPTED_RTP_HEADER_PART_SIZE)

    @JvmField
    protected var packetHasPadding = false

    @JvmField
    protected var csrcCount = 0

    @JvmField
    protected var encryptedExtensionPartLength = 0 // -1 if there is no extension

    protected fun readUnencryptedRtpHeaderPart(packet: ByteReadPacket): Int {
        val packetSize = packet.remaining
        if (packetSize < MIN_UNENCRYPTED_RTP_HEADER_PART_SIZE) {
            return -1
        }

        // read the first two octets of the header
        val octet1 = packet.readByte().toInt()
        val octet2 = packet.readByte()

        // check the version
        if ((octet1 and VERSION_MASK) != VERSION_2) {
            return -1
        }
        val hasExtension = (octet1 and EXTENSION_MASK) != 0
        val csrcCnt = octet1 and CSRC_COUNT_MASK
        val headerSize = MIN_UNENCRYPTED_RTP_HEADER_PART_SIZE +
            (csrcCnt * CSRC_SIZE) +
            (if (hasExtension) EXTENSION_PREAMBLE_SIZE else 0)
        if (packetSize < headerSize) {
            return -1
        }

        // check the payload type
        if (octet2 != PAYLOAD_TYPE) {
            return -1
        }

        // read the remaining unencrypted part of the header
        unencryptedRtpHeaderPartBuffer[0] = octet1.toByte()
        unencryptedRtpHeaderPartBuffer[1] = octet2
        packet.readFully(dst = unencryptedRtpHeaderPartBuffer, offset = 2, length = headerSize - 2)

        encryptedExtensionPartLength = if (hasExtension) {
            val extensionLength = unencryptedRtpHeaderPartBuffer
                .readShortBigEndian(offset = headerSize - 2).toInt()
                .and(0xFFFF)
            if (packetSize < headerSize + (extensionLength * EXTENSION_WORD_SIZE)) {
                return -1
            }
            extensionLength
        } else {
            -1
        }
        packetHasPadding = (octet1 and PADDING_MASK) != 0
        csrcCount = csrcCnt

        return headerSize
    }
}
