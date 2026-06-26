/*
 * Wake — the platform broadcaster selector.
 *
 * A one-line `expect fun` (CLAUDE.md §8 permits a tiny `expect fun`; it bans a
 * large `expect class`) that hands [com.happycodelucky.wake.Wake.up] the right
 * [UdpBroadcaster] for the current target. The actuals return
 * `PosixUdpBroadcaster` on Apple and `JvmUdpBroadcaster` on Android. The
 * broadcaster stays an `internal interface` so commonTest can substitute a
 * recording fake against [performWake]; only the platform selection crosses the
 * expect/actual boundary.
 */
package com.happycodelucky.wake.internal

/** The platform [UdpBroadcaster] for the current target. */
internal expect fun defaultBroadcaster(): UdpBroadcaster
