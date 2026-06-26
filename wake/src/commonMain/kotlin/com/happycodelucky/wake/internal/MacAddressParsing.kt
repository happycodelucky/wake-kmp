/*
 * Wake — MAC address parsing.
 *
 * Pure, platform-agnostic. Accepts the three common textual MAC formats and
 * normalizes to a 6-byte big-endian array. Fully unit-tested from commonTest.
 */
package com.happycodelucky.wake.internal

private const val HEX_DIGITS_PER_BYTE = 2
private const val HEX_NIBBLE_BITS = 4
private const val DECIMAL_TEN = 10

/**
 * Parse [text] into a normalized 6-byte MAC address.
 *
 * Accepts colon-separated (`AA:BB:CC:DD:EE:FF`), hyphen-separated
 * (`aa-bb-cc-dd-ee-ff`), and bare (`aabbccddeeff`) hexadecimal, all
 * case-insensitive. Any surrounding whitespace is trimmed. Returns `null` for
 * anything that isn't exactly twelve hex digits once separators are stripped —
 * the caller maps `null` to
 * [com.happycodelucky.wake.WakeResult.InvalidMacAddress].
 *
 * @return a [MAC_LENGTH]-byte array, or `null` if [text] is not a valid MAC.
 */
@Suppress("ReturnCount") // Guard-clause early-returns are clearer than nesting here.
internal fun parseMacBytes(text: String): ByteArray? {
    // Strip the two accepted separators and surrounding whitespace, leaving
    // only the hex digits. We deliberately do NOT strip arbitrary characters:
    // a stray separator like "AA.BB.CC..." leaves dots in place, fails the
    // hex check below, and is correctly rejected.
    val hex = text.trim().replace(":", "").replace("-", "")
    if (hex.length != MAC_LENGTH * HEX_DIGITS_PER_BYTE) return null

    val bytes = ByteArray(MAC_LENGTH)
    for (i in 0 until MAC_LENGTH) {
        val high = hexDigitValue(hex[i * HEX_DIGITS_PER_BYTE]) ?: return null
        val low = hexDigitValue(hex[i * HEX_DIGITS_PER_BYTE + 1]) ?: return null
        bytes[i] = ((high shl HEX_NIBBLE_BITS) or low).toByte()
    }
    return bytes
}

/** Value of a single hex digit (`0-9`, `a-f`, `A-F`), or `null` if not hex. */
private fun hexDigitValue(c: Char): Int? =
    when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + DECIMAL_TEN
        in 'A'..'F' -> c - 'A' + DECIMAL_TEN
        else -> null
    }
