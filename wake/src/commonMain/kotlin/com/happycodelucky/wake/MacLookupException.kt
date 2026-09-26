/*
 * Wake — the failure half of `lookupMac`'s `Result` (CLAUDE.md §8).
 *
 * Mirrors [WakeException]: a sealed `Exception` hierarchy carried by
 * `kotlin.Result<String>` in Kotlin, and mapped by the bundled macOS Swift onto
 * a native `MacLookupError` enum.
 *
 * There is deliberately NO `Unsupported` case. ARP-cache reads are impossible on
 * iOS (the kernel returns a spoofed address) and on modern Android (`/proc/net/arp`
 * is SELinux-blocked), so the lookup entry point (`lookupMac`) is not declared on
 * those targets at all — calling it there is a compile error, not a runtime
 * `Unsupported`. This type therefore only models the failures that can occur
 * where the lookup genuinely exists (macOS and the JVM desktop).
 */
package com.happycodelucky.wake

/**
 * Why an ARP-cache MAC lookup (`lookupMac`, macOS and JVM desktop) produced no
 * address. Always the exception inside a failed `Result<String>` from
 * `lookupMac`.
 */
public sealed class MacLookupException(
    message: String,
) : Exception(message) {
    /** Non-null: every [MacLookupException] is constructed with a message. */
    override val message: String
        get() = super.message.orEmpty()

    /**
     * The supplied string is not a dotted-quad IPv4 address.
     *
     * @property ip the input that failed to parse, verbatim.
     */
    public class InvalidIpAddress(
        public val ip: String,
    ) : MacLookupException("could not parse IPv4 address: \"$ip\"")

    /**
     * The IPv4 address was valid but has no current ARP-cache entry.
     *
     * Common for an idle host or one whose entry has aged out — it does not mean
     * the host is absent from the network. Contacting the host first (e.g. a ping
     * or any datagram) typically populates the cache.
     *
     * @property ip the address that was looked up.
     */
    public class NotInCache(
        public val ip: String,
    ) : MacLookupException("no ARP entry for $ip")

    /**
     * The OS reported an error while reading the ARP cache. [message] describes
     * the failure.
     */
    public class LookupFailed(
        message: String,
    ) : MacLookupException(message)
}
