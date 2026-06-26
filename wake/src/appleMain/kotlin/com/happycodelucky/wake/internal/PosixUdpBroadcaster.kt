/*
 * Wake — Apple UDP broadcaster (POSIX sockets via cinterop).
 *
 * CLAUDE.md §5/§6: the platform primitive is genuinely simple here, and Ktor's
 * UDP path crashes on Apple (KTOR-6489), so we use raw POSIX sockets directly:
 * socket → setsockopt(SO_BROADCAST) → sendto → close. The socket primitives
 * come from `platform.posix`, which Kotlin/Native exposes 1:1 across iOS and
 * macOS, so this single appleMain implementation serves every Apple target.
 *
 * Two C helpers we deliberately do NOT use from cinterop:
 *   - `htons` is a macro on Darwin and cannot be bound; we compute network byte
 *     order in pure Kotlin ([portToNetworkOrder]).
 *   - `inet_pton` is not exported by `platform.posix` on Darwin (arpa/inet.h is
 *     not in the platform def's header set); we build the 32-bit address with
 *     [parseIpv4] + [hostToNetworkOrder] and assign `sin_addr.s_addr` directly.
 *
 * Verified against Kotlin 2.3.21 `platform.posix` (macos_arm64 platform def):
 * socket / setsockopt / sendto / close are present, as are `sockaddr_in` with
 * `sin_family` / `sin_port` / `sin_addr` (a nested `in_addr` with `s_addr`).
 */
@file:OptIn(ExperimentalForeignApi::class)

package com.happycodelucky.wake.internal

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.posix.AF_INET
import platform.posix.IPPROTO_UDP
import platform.posix.SOCK_DGRAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_BROADCAST
import platform.posix.close
import platform.posix.errno
import platform.posix.sendto
import platform.posix.setsockopt
import platform.posix.sockaddr
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.uint32_tVar

internal class PosixUdpBroadcaster : UdpBroadcaster {
    override suspend fun send(
        packet: ByteArray,
        broadcastAddress: String,
        port: Int,
    ): WakeSendOutcome {
        val hostOrderAddress =
            parseIpv4(broadcastAddress)
                ?: return WakeSendOutcome.Failed("invalid broadcast address: $broadcastAddress")
        val networkOrderAddress = hostToNetworkOrder(hostOrderAddress)

        // Apple Kotlin/Native has no Dispatchers.IO; Default is the documented
        // choice for a short blocking syscall.
        return withContext(Dispatchers.Default) {
            memScoped {
                val fd = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP)
                if (fd < 0) {
                    return@memScoped WakeSendOutcome.Failed("socket() failed, errno=$errno")
                }
                try {
                    // Enable broadcast on the socket.
                    val enable = alloc<uint32_tVar>()
                    enable.value = 1u
                    val optResult =
                        setsockopt(
                            fd,
                            SOL_SOCKET,
                            SO_BROADCAST,
                            enable.ptr,
                            sizeOf<uint32_tVar>().convert(),
                        )
                    if (optResult != 0) {
                        return@memScoped WakeSendOutcome.Failed("setsockopt(SO_BROADCAST) failed, errno=$errno")
                    }

                    // Destination address — fields are assigned in network byte
                    // order, computed in pure Kotlin (see file header).
                    val addr = alloc<sockaddr_in>()
                    addr.sin_family = AF_INET.convert()
                    addr.sin_port = portToNetworkOrder(port)
                    addr.sin_addr.s_addr = networkOrderAddress

                    val sent =
                        packet.usePinned { pinned ->
                            sendto(
                                fd,
                                pinned.addressOf(0),
                                packet.size.convert(),
                                0,
                                addr.ptr.reinterpret<sockaddr>(),
                                sizeOf<sockaddr_in>().convert(),
                            )
                        }

                    if (sent < 0) {
                        WakeSendOutcome.Failed("sendto() failed, errno=$errno")
                    } else {
                        WakeSendOutcome.Sent
                    }
                } finally {
                    close(fd)
                }
            }
        }
    }
}
