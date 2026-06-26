/*
 * Wake — Android-side public factory.
 *
 * Top-level function (not a companion-object factory) per CLAUDE.md §8. Unlike
 * the sibling reachability library, Wake's Android factory needs no `Context`:
 * a plain limited UDP broadcast goes through `java.net.DatagramSocket` without
 * any system service. A `Context`-taking overload is reserved for future
 * `WifiManager` multicast-lock support (see README); adding it later is
 * source-compatible.
 */
package com.happycodelucky.wake

import com.happycodelucky.wake.internal.DefaultWake
import com.happycodelucky.wake.internal.JvmUdpBroadcaster

/**
 * Construct a [Wake] backed by Android's [java.net.DatagramSocket].
 *
 * Requires the `android.permission.INTERNET` permission, which the library
 * declares in its own manifest so it merges into the consuming app — consumers
 * do not need to add it themselves, and no runtime grant is required (INTERNET
 * is a normal-protection permission).
 *
 * Stateless and reusable — there is nothing to close.
 *
 * Note: on some Wi-Fi chipsets, broadcast egress may require a held
 * `WifiManager.MulticastLock`. That is not handled in this version; a
 * `Context`-taking factory overload will be added when multicast-lock support
 * lands. For wired/most Wi-Fi setups the plain broadcast works as-is.
 */
@Suppress("FunctionName")
public fun Wake(): Wake = DefaultWake(JvmUdpBroadcaster())
