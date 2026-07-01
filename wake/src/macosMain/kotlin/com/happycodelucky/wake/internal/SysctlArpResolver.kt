/*
 * Wake — macOS ARP resolver (sysctl routing-table walk via cinterop).
 *
 * Lives in `macosMain` (not `appleMain`): the ARP read works only on macOS. iOS
 * shares `platform.posix` but the kernel hands sandboxed apps a spoofed MAC, so
 * iOS has no resolver and no `lookupMac` at all — and because this file and the
 * `arp` cinterop are macOS-only, the ARP C code never links into an iOS slice.
 *
 * The heavy lifting (the variable-length sockaddr walk) is in the C shim
 * `wake_arp_lookup` from the `arp` cinterop; this Kotlin just marshals the IPv4
 * address into network byte order, allocates the 6-byte output buffer, and maps
 * the shim's return code. Mirrors `PosixUdpBroadcaster`'s cinterop idiom:
 * `memScoped` + `Dispatchers.Default` (Apple Kotlin/Native has no `Dispatchers.IO`).
 */
@file:OptIn(ExperimentalForeignApi::class)

package com.happycodelucky.wake.internal

import com.happycodelucky.wake.cinterop.arp.wake_arp_lookup
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// wake_arp_lookup return codes (see arp.def): 0 found, 1 not found, <0 -errno.
private const val ARP_RC_FOUND = 0
private const val ARP_RC_NOT_FOUND = 1

internal class SysctlArpResolver : ArpResolver {
    override suspend fun resolve(ipv4: String): ArpLookupOutcome {
        val hostOrder =
            parseIpv4(ipv4)
                ?: return ArpLookupOutcome.Failed("invalid IPv4 address: $ipv4")
        // The cache is keyed on the network-order address (as in
        // sockaddr_in.sin_addr.s_addr); the shim compares against that field.
        val networkOrder = hostToNetworkOrder(hostOrder)

        // Apple Kotlin/Native has no Dispatchers.IO; Default is the documented
        // choice for a short blocking syscall (mirrors PosixUdpBroadcaster).
        return withContext(Dispatchers.Default) {
            memScoped {
                val out = allocArray<UByteVar>(MAC_LENGTH)
                when (val rc = wake_arp_lookup(networkOrder, out)) {
                    ARP_RC_FOUND -> ArpLookupOutcome.Resolved(ByteArray(MAC_LENGTH) { i -> out[i].toByte() })
                    ARP_RC_NOT_FOUND -> ArpLookupOutcome.NotFound
                    else -> ArpLookupOutcome.Failed("sysctl ARP read failed, rc=$rc")
                }
            }
        }
    }
}
