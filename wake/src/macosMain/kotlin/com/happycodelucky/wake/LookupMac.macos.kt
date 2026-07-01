/*
 * Wake — macOS ARP lookup entry point (reaches SwiftUI / AppKit consumers).
 *
 * Declared in `macosMain` (not `commonMain`): reading the ARP cache is only
 * possible on macOS and the JVM desktop. A top-level function rather than a
 * `Wake` member — a concrete `object` cannot gain members from a platform source
 * set — so SKIE renders it as a global Swift async function:
 * `let r = try await lookupMac(ip: "192.168.1.42")`. (The matching JVM slice
 * declares its own identically-named top-level `lookupMac`; disjoint compilations,
 * no clash.)
 *
 * iOS deliberately has no such function: the kernel returns a spoofed MAC there,
 * so calling `lookupMac` on iOS is a compile error, not a runtime failure.
 */
@file:OptIn(ExperimentalObjCName::class)

package com.happycodelucky.wake

import com.happycodelucky.wake.internal.SysctlArpResolver
import com.happycodelucky.wake.internal.performLookup
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Look up the hardware (MAC) address for [ip] in the macOS host's ARP cache.
 *
 * Reads the live ARP table via the BSD routing-socket `sysctl` interface. The
 * cache only holds an entry for a host the machine has recently communicated
 * with, so [MacLookupResult.NotInCache] is a normal result for an idle host —
 * contact it first (e.g. a ping) to populate the cache. On macOS 15+, the
 * accompanying broadcast send ([Wake.up]) may surface the Local Network privacy
 * prompt; the ARP read itself is not gated, but an OS error folds to
 * [MacLookupResult.Error].
 *
 * Never throws: an unparseable [ip] or an OS read error is returned as
 * [MacLookupResult.Error]. A resolved [MacLookupResult.Found.macAddress] is in the
 * canonical `AA:BB:CC:DD:EE:FF` form that [Wake.up] accepts.
 *
 * ### Swift
 *
 * SKIE renders this as a global `async` function (it is not a member of `Wake`):
 *
 * ```swift
 * switch try await lookupMac(ip: "192.168.1.42") {
 * case let .found(macAddress): _ = try await Wake.up(mac: macAddress)
 * case .notInCache: print("no ARP entry — ping it first")
 * case let .error(message): print("lookup failed: \(message)")
 * }
 * ```
 *
 * @param ip the target device's IPv4 address, in dotted-quad form.
 * @return [MacLookupResult.Found] with the resolved MAC, [MacLookupResult.NotInCache]
 *   when there is no current entry, or [MacLookupResult.Error] for a bad IP or a
 *   read failure.
 */
@ObjCName(swiftName = "lookupMac")
public suspend fun lookupMac(ip: String): MacLookupResult = performLookup(resolver = SysctlArpResolver(), ip = ip)
