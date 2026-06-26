/*
 * Wake — JVM/Android platform broadcaster selector.
 *
 * One `actual` for both the JVM and Android targets: they share the
 * `java.net.DatagramSocket` broadcaster verbatim (this file lives in the
 * `jvmShared` intermediate source set that both leaves depend on). No `Context`
 * is needed for a plain limited UDP broadcast; a `Context`-taking path is
 * reserved for future Android `WifiManager` multicast-lock support (see README).
 * See `JvmUdpBroadcaster`.
 */
package com.happycodelucky.wake.internal

internal actual fun defaultBroadcaster(): UdpBroadcaster = JvmUdpBroadcaster()
