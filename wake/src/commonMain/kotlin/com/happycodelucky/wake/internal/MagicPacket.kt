/*
 * Wake — magic-packet construction.
 *
 * Pure, platform-agnostic byte arithmetic — the heart of Wake-on-LAN. Fully
 * unit-tested from commonTest with zero platform mocking (mirrors the
 * "pure logic in commonMain/internal" pattern the sibling repos use).
 */
package com.happycodelucky.wake.internal

/** Length of a hardware (MAC) address in bytes. */
internal const val MAC_LENGTH: Int = 6

/** Number of times the MAC is repeated in the magic-packet body. */
internal const val MAC_REPETITIONS: Int = 16

/** Total magic-packet length: a 6-byte sync stream + 16 MAC repetitions. */
internal const val MAGIC_PACKET_LENGTH: Int = MAC_LENGTH + MAC_REPETITIONS * MAC_LENGTH

/**
 * Build the Wake-on-LAN magic packet for [mac].
 *
 * The packet is a fixed 102 bytes: a "sync stream" of six `0xFF` bytes,
 * followed by the target's 6-byte MAC address repeated sixteen times. A NIC in
 * Wake-on-LAN mode scans incoming frames for this exact pattern addressed to
 * its own MAC and powers the host on when it matches.
 *
 * @param mac exactly [MAC_LENGTH] bytes, big-endian wire order.
 * @return a new [MAGIC_PACKET_LENGTH]-byte array.
 * @throws IllegalArgumentException if [mac] is not exactly 6 bytes. This is an
 *   internal invariant: callers parse and validate the MAC before reaching
 *   here, so a violation is a programming error, not user input.
 */
internal fun buildMagicPacket(mac: ByteArray): ByteArray {
    require(mac.size == MAC_LENGTH) { "MAC must be $MAC_LENGTH bytes, was ${mac.size}" }
    return ByteArray(MAGIC_PACKET_LENGTH).apply {
        for (i in 0 until MAC_LENGTH) {
            this[i] = 0xFF.toByte()
        }
        for (repetition in 0 until MAC_REPETITIONS) {
            mac.copyInto(this, destinationOffset = MAC_LENGTH + repetition * MAC_LENGTH)
        }
    }
}
