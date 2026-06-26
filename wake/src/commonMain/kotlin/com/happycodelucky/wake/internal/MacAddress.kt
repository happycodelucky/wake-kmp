/*
 * Wake — validated MAC address value type.
 */
package com.happycodelucky.wake.internal

import kotlin.jvm.JvmInline

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
