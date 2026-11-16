package com.example.androidwebsiteblocker.core.protection

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted PBKDF2 hashing for block set PINs, so the PIN isn't readable from the app's storage.
 * Stored format: `pbkdf2-sha256$<iterations>$<salt b64>$<hash b64>`.
 * There is no recovery: a forgotten PIN means clearing app data.
 */
object PinHasher {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PREFIX = "pbkdf2-sha256"
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 12

    fun isValidPin(pin: String): Boolean = pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }

    fun hash(pin: String, random: SecureRandom = SecureRandom()): String {
        require(isValidPin(pin)) { "PIN must be $MIN_LENGTH-$MAX_LENGTH digits" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val b64 = Base64.getEncoder()
        return listOf(PREFIX, ITERATIONS.toString(), b64.encodeToString(salt), b64.encodeToString(derive(pin, salt, ITERATIONS)))
            .joinToString("$")
    }

    fun verify(pin: String, stored: String): Boolean {
        val parts = stored.split('$')
        if (parts.size != 4 || parts[0] != PREFIX) return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val b64 = Base64.getDecoder()
        val salt = b64.decode(parts[2])
        val expected = b64.decode(parts[3])
        return MessageDigest.isEqual(derive(pin, salt, iterations), expected)
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
