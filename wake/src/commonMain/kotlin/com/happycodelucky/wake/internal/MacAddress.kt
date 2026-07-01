/*
 * Wake — validated MAC address value type.
 */
package com.happycodelucky.wake.internal

import kotlin.jvm.JvmInline

private const val HEX_RADIX = 16
private const val HEX_DIGITS_PER_OCTET = 2

// Low-byte mask, named (not a raw 0xFF) to stay clear of detekt's MagicNumber —
// mirrors the convention in Ipv4.kt.
private const val BYTE_MASK = 0xFF

/**
 * A validated 48-bit hardware (MAC) address.
 *
 * `internal` and constructed only through [parseMacBytes] → [MacAddress.of], so
 * a [MacAddress] always wraps exactly [MAC_LENGTH] bytes in big-endian wire
 * order. Kept as a zero-cost `value class` for the construction path; the
 * public API ([com.happycodelucky.wake.Wake.up]) takes a `String` and never
 * exposes this type across the Swift boundary.
 */
@JvmInline
internal value class MacAddress private constructor(
    val bytes: ByteArray,
) {
    internal companion object {
        /** Wrap [bytes], which the caller guarantees is exactly [MAC_LENGTH] long. */
        fun of(bytes: ByteArray): MacAddress {
            require(bytes.size == MAC_LENGTH) { "MAC must be $MAC_LENGTH bytes, was ${bytes.size}" }
            return MacAddress(bytes)
        }
    }
}

/**
 * Format [bytes] as an uppercase, colon-separated MAC string — e.g.
 * `AA:BB:CC:DD:EE:FF`.
 *
 * The inverse of [parseMacBytes]: each byte becomes two zero-padded uppercase hex
 * digits joined by `:`. Used by the ARP-cache lookup to render a resolved
 * hardware address as the canonical text form the public API exposes (and that
 * [com.happycodelucky.wake.Wake.up] accepts back), so a lookup result feeds
 * straight into a wake. Pure and platform-agnostic — unit-tested from commonTest.
 *
 * @param bytes exactly [MAC_LENGTH] bytes, big-endian wire order.
 * @return the canonical uppercase colon-separated MAC string.
 * @throws IllegalArgumentException if [bytes] is not exactly [MAC_LENGTH] long.
 *   An internal invariant: resolvers hand back a fixed-width address, so a
 *   violation is a programming error, not user input.
 */
internal fun formatMac(bytes: ByteArray): String {
    require(bytes.size == MAC_LENGTH) { "MAC must be $MAC_LENGTH bytes, was ${bytes.size}" }
    return bytes.joinToString(separator = ":") { byte ->
        (byte.toInt() and BYTE_MASK)
            .toString(HEX_RADIX)
            .uppercase()
            .padStart(HEX_DIGITS_PER_OCTET, '0')
    }
}
