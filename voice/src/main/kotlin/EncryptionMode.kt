@file:Generate(
    STRING_KORD_ENUM, name = "EncryptionMode",
    docUrl = "https://discord.com/developers/docs/topics/voice-connections#transport-encryption-modes",
    entries = [
        Entry("AeadAes256GcmRtpSize", stringValue = "aead_aes256_gcm_rtpsize"),
        Entry("AeadXChaCha20Poly1305RtpSize", stringValue = "aead_xchacha20_poly1305_rtpsize"),
        Entry(
            "XSalsa20Poly1305", stringValue = "xsalsa20_poly1305",
            deprecated = Deprecated(MESSAGE, level = DeprecationLevel.WARNING),
        ),
        Entry(
            "XSalsa20Poly1305Suffix", stringValue = "xsalsa20_poly1305_suffix",
            deprecated = Deprecated(MESSAGE, level = DeprecationLevel.WARNING),
        ),
        Entry(
            "XSalsa20Poly1305Lite", stringValue = "xsalsa20_poly1305_lite",
            deprecated = Deprecated(MESSAGE, level = DeprecationLevel.WARNING),
        ),
    ]
)

package dev.kord.voice

import dev.kord.ksp.Generate
import dev.kord.ksp.Generate.EntityType.STRING_KORD_ENUM
import dev.kord.ksp.Generate.Entry

private const val MESSAGE = "XSalsa20 Poly1305 encryption is deprecated for Discord voice connections and will be " +
    "discontinued as of November 18th, 2024. As of this date, the voice gateway will not allow you to connect with " +
    "one of the deprecated encryption modes. The deprecation level will be raised to ERROR in 0.16.0, to HIDDEN in " +
    "0.17.0, and this declaration will be removed in 0.18.0."
