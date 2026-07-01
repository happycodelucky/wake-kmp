/*
 * Wake — the ARP-cache read seam.
 *
 * `ArpResolver` is the point of platform divergence for the MAC lookup: it reads
 * the OS ARP cache for one IPv4 address. Implemented over a `sysctl` routing-table
 * walk on macOS (`SysctlArpResolver`, in macosMain) and `/proc/net/arp` / the
 * `arp` command on the JVM desktop (`JvmArpResolver`, in jvmMain).
 *
 * Unlike the UDP send seam, this is a plain `internal interface` with NO
 * `expect`/`actual` selector. The lookup entry point is declared per-source-set
 * (only where ARP is readable), so each platform's `lookupMac` constructs its own
 * resolver directly — there is no common `defaultArpResolver()` to select one,
 * and iOS/Android never see this interface implemented at all. Keeping it an
 * `internal interface` still lets commonTest substitute a recording fake and
 * unit-test [performLookup]'s orchestration with no real OS calls.
 */
package com.happycodelucky.wake.internal

/**
 * Reads the OS ARP cache to resolve an IPv4 address to a hardware address.
 */
internal interface ArpResolver {
    /**
     * Look up [ipv4] (already validated as a dotted-quad by the caller) in the OS
     * ARP cache.
     *
     * Implementations perform the read off the caller's thread (the OS read can
     * block) and never throw — failures are returned as [ArpLookupOutcome.Failed].
     */
    suspend fun resolve(ipv4: String): ArpLookupOutcome
}

/**
 * The narrow internal outcome of an [ArpResolver.resolve]. [performLookup] maps
 * this onto the public [com.happycodelucky.wake.MacLookupResult].
 */
internal sealed interface ArpLookupOutcome {
    /**
     * The address was found; [mac] is exactly [MAC_LENGTH] bytes in big-endian
     * wire order.
     */
    data class Resolved(
        val mac: ByteArray,
    ) : ArpLookupOutcome

    /** The address was not present in the ARP cache (or its entry was incomplete). */
    data object NotFound : ArpLookupOutcome

    /** The read failed; [message] describes the platform error. */
    data class Failed(
        val message: String,
    ) : ArpLookupOutcome
}
