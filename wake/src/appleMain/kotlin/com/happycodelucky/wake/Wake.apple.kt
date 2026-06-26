/*
 * Wake — Apple-side public factory.
 *
 * Top-level function (not a companion-object factory) per CLAUDE.md §8:
 * "No companion-object factories for Swift-facing entry points. Top-level
 * functions or constructors." SKIE renders this as a Swift module-level
 * `Wake()` constructor-style call.
 */
package com.happycodelucky.wake

import com.happycodelucky.wake.internal.DefaultWake
import com.happycodelucky.wake.internal.PosixUdpBroadcaster

/**
 * Construct a [Wake] backed by POSIX UDP broadcast sockets.
 *
 * Available on iOS, iPadOS, and macOS (the `appleMain` source set covers all
 * Apple targets in this module). Stateless and reusable — there is nothing to
 * close.
 *
 * On a host with multiple active interfaces (VPN, Wi-Fi + cellular) the OS
 * chooses the egress interface for the limited broadcast; pinning a specific
 * interface (`IP_BOUND_IF`) is future work. See the README for details.
 *
 * Reads as `Wake()` in both Kotlin and Swift: the framework module is named
 * `WakeKit`, so the `Wake` type and `Wake()` factory don't collide with the
 * module name on the Swift side.
 */
@Suppress("FunctionName")
public fun Wake(): Wake = DefaultWake(PosixUdpBroadcaster())
