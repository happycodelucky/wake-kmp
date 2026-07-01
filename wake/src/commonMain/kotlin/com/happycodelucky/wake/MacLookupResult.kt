/*
 * Wake — ARP-cache lookup result (CLAUDE.md §8).
 *
 * A `sealed interface` rather than `kotlin.Result`, for the same reason as
 * [WakeResult]: SKIE renders it as an exhaustive Swift enum, so consumers get a
 * compiler-checked `switch` with structured payloads instead of an opaque
 * `KotlinResult`.
 *
 * There is deliberately NO `Unsupported` case. ARP-cache reads are impossible on
 * iOS (the kernel returns a spoofed address) and on modern Android (`/proc/net/arp`
 * is SELinux-blocked), so the lookup entry point (`lookupMac`) is not declared on
 * those targets at all — calling it there is a compile error, not a runtime
 * `Unsupported`. This type therefore only ever models the outcomes that can occur
 * where the lookup genuinely exists (macOS and the JVM desktop).
 */
package com.happycodelucky.wake

/**
 * The outcome of an ARP-cache MAC lookup for an IPv4 address.
 *
 * Returned by the platform `lookupMac(ip)` entry points (macOS and JVM desktop).
 * The ARP cache only holds an entry for a host the machine has recently
 * communicated with, so [NotInCache] is a normal, expected result for a host that
 * is idle or has aged out — it does not mean the host is absent from the network,
 * only that there is no current IP→MAC mapping to read.
 */
public sealed interface MacLookupResult {
    /**
     * The IPv4 address was present in the ARP cache.
     *
     * @property macAddress the resolved hardware address, formatted as canonical
     *   uppercase colon-separated hex (`AA:BB:CC:DD:EE:FF`) — the same form
     *   [Wake.up] accepts, so a [Found] result feeds straight into a wake.
     */
    public data class Found(
        val macAddress: String,
    ) : MacLookupResult

    /**
     * The IPv4 address was syntactically valid but has no current ARP-cache
     * entry. Common for an idle host or one whose entry has aged out; contacting
     * the host first (e.g. a ping or any datagram) typically populates the cache.
     */
    public data object NotInCache : MacLookupResult

    /**
     * The lookup could not be performed: the supplied IP was unparseable, or the
     * OS reported an error while reading the ARP cache.
     *
     * @property message a human-readable description of the failure.
     */
    public data class Error(
        val message: String,
    ) : MacLookupResult
}
