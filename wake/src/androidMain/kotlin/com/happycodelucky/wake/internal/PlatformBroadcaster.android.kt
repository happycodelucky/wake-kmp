/*
 * Wake — Android platform broadcaster selector.
 *
 * Returns the `java.net.DatagramSocket` broadcaster. No `Context` is needed for
 * a plain limited UDP broadcast; a `Context`-taking path is reserved for future
 * `WifiManager` multicast-lock support (see README). See `JvmUdpBroadcaster`.
 */
package com.happycodelucky.wake.internal

internal actual fun defaultBroadcaster(): UdpBroadcaster = JvmUdpBroadcaster()
