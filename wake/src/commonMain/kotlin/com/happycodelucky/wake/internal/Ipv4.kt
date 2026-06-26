/*
 * Wake — IPv4 dotted-quad parsing.
 *
 * Pure, platform-agnostic. Used by the Apple POSIX broadcaster, which cannot
 * rely on `inet_pton` (not exported by Kotlin/Native's `platform.posix` on
 * Darwin) and so builds the 32-bit address itself. Computing the address in
 * common Kotlin also makes it unit-testable without a socket.
 */
package com.happycodelucky.wake.internal

private const val IPV4_OCTET_COUNT = 4
private const val MAX_OCTET_DIGITS = 3
private const val MAX_OCTET_VALUE = 255
private const val BYTE_MASK = 0xFFu
private const val BYTE_MASK_INT = 0xFF

// Bit offsets of each byte within a 32-bit word, byte 0 = least significant.
private const val SHIFT_BYTE_0 = 0
private const val SHIFT_BYTE_1 = 8
private const val SHIFT_BYTE_2 = 16
private const val SHIFT_BYTE_3 = 24

/**
 * Parse a dotted-quad IPv4 address (e.g. `192.168.1.255`) into its 32-bit
 * value in **host bit order** — octet 0 in the most-significant byte.
 *
 * Returns `null` for anything that isn't exactly four decimal octets in
 * `0..255`. The caller is responsible for converting to network byte order if
 * the target API expects it (see [hostToNetworkOrder]).
 *
 * @return the address as an unsigned 32-bit value (carried in a [UInt]), or
 *   `null` if [text] is not a valid dotted-quad IPv4 address.
 */
@Suppress("ReturnCount") // Guard-clause early-returns are clearer than nesting here.
internal fun parseIpv4(text: String): UInt? {
    val parts = text.trim().split(".")
    if (parts.size != IPV4_OCTET_COUNT) return null

    var result = 0u
    for (part in parts) {
        val octet = parseOctet(part) ?: return null
        result = (result shl SHIFT_BYTE_1) or octet.toUInt()
    }
    return result
}

/** Parse a single dotted-quad octet (`0..255`), or `null` if out of range / malformed. */
private fun parseOctet(part: String): Int? {
    // Reject empties, signs, and non-digits before delegating to toIntOrNull.
    if (part.isEmpty() || part.length > MAX_OCTET_DIGITS || !part.all { it in '0'..'9' }) return null
    return part.toIntOrNull()?.takeIf { it in 0..MAX_OCTET_VALUE }
}

/**
 * Swap a 32-bit value from host-order (as produced by [parseIpv4]) to network
 * byte order (big-endian). On the little-endian ARM targets we ship this
 * reverses the four bytes; the result is what a `sockaddr_in.sin_addr.s_addr`
 * field expects.
 */
internal fun hostToNetworkOrder(value: UInt): UInt {
    val byte0 = (value shr SHIFT_BYTE_0) and BYTE_MASK
    val byte1 = (value shr SHIFT_BYTE_1) and BYTE_MASK
    val byte2 = (value shr SHIFT_BYTE_2) and BYTE_MASK
    val byte3 = (value shr SHIFT_BYTE_3) and BYTE_MASK
    return (byte0 shl SHIFT_BYTE_3) or
        (byte1 shl SHIFT_BYTE_2) or
        (byte2 shl SHIFT_BYTE_1) or
        (byte3 shl SHIFT_BYTE_0)
}

/**
 * Convert a port number to network byte order (big-endian) as a 16-bit value
 * carried in a [UShort]. Equivalent to C's `htons` on a little-endian host,
 * computed in pure Kotlin so we don't depend on the `htons` macro (which
 * Kotlin/Native cannot bind).
 */
internal fun portToNetworkOrder(port: Int): UShort {
    val low = port and BYTE_MASK_INT
    val high = (port shr SHIFT_BYTE_1) and BYTE_MASK_INT
    return ((low shl SHIFT_BYTE_1) or high).toUShort()
}
