/*
 * Wake — Apple platform broadcaster selector.
 *
 * Returns the POSIX-socket broadcaster shared by every Apple target (iOS,
 * iPadOS, macOS); `platform.posix` is exposed 1:1 across them, so a single
 * appleMain actual suffices. See `PosixUdpBroadcaster` for the socket details.
 */
package com.happycodelucky.wake.internal

internal actual fun defaultBroadcaster(): UdpBroadcaster = PosixUdpBroadcaster()
