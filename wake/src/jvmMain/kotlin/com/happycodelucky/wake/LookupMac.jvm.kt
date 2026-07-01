/*
 * Wake — JVM-desktop ARP lookup entry point.
 *
 * Declared in `jvmMain` (not `commonMain`), because reading the ARP cache is only
 * possible on the JVM desktop and macOS — not iOS or Android. A top-level function
 * rather than a `Wake` member: a concrete `object` cannot gain members from a
 * platform source set, and the macOS slice declares its own identically-named
 * top-level `lookupMac` (disjoint compilations, no clash).
 *
 * This is the function the `:apps:cli` "wake by IP" path calls.
 */
package com.happycodelucky.wake

import com.happycodelucky.wake.internal.JvmArpResolver
import com.happycodelucky.wake.internal.performLookup

/**
 * Look up the hardware (MAC) address for [ip] in the JVM host's ARP cache.
 *
 * Reads `/proc/net/arp` on Linux, or shells out to the `arp` command on macOS /
 * Windows / BSD. The ARP cache only holds an entry for a host the machine has
 * recently communicated with, so [MacLookupResult.NotInCache] is a normal result
 * for an idle host — contact it first (e.g. a ping) to populate the cache.
 *
 * Never throws: an unparseable [ip] or an OS read error is returned as
 * [MacLookupResult.Error]. A resolved [MacLookupResult.Found.macAddress] is in the
 * canonical `AA:BB:CC:DD:EE:FF` form that [Wake.up] accepts, so it can be passed
 * straight to a wake.
 *
 * ```kotlin
 * when (val result = lookupMac("192.168.1.42")) {
 *     is MacLookupResult.Found -> Wake.up(result.macAddress)
 *     is MacLookupResult.NotInCache -> println("no ARP entry — ping it first")
 *     is MacLookupResult.Error -> println("lookup failed: ${result.message}")
 * }
 * ```
 *
 * @param ip the target device's IPv4 address, in dotted-quad form.
 * @return [MacLookupResult.Found] with the resolved MAC, [MacLookupResult.NotInCache]
 *   when there is no current entry, or [MacLookupResult.Error] for a bad IP or a
 *   read failure.
 */
public suspend fun lookupMac(ip: String): MacLookupResult = performLookup(resolver = JvmArpResolver(), ip = ip)
