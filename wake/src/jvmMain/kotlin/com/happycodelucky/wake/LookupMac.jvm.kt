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
 * recently communicated with, so [MacLookupException.NotInCache] is a normal
 * failure for an idle host — contact it first (e.g. a ping) to populate the cache.
 *
 * Never throws (other than coroutine cancellation): an unparseable [ip], a
 * missing entry, or an OS read error is a failed `Result` holding a
 * [MacLookupException]. A resolved MAC is in the canonical `AA:BB:CC:DD:EE:FF`
 * form that [Wake.up] accepts, so it can be passed straight to a wake.
 *
 * ```kotlin
 * lookupMac("192.168.1.42")
 *     .mapCatching { mac -> Wake.up(mac).getOrThrow() }
 *     .onFailure { e -> println("could not wake: ${e.message}") }
 * ```
 *
 * @param ip the target device's IPv4 address, in dotted-quad form.
 * @return the resolved MAC, or a failure holding a [MacLookupException].
 */
public suspend fun lookupMac(ip: String): Result<String> = performLookup(resolver = JvmArpResolver(), ip = ip)
