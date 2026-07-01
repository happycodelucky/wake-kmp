package com.happycodelucky.wake.internal

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertIs

/**
 * Hermetic tests of the JVM ARP resolver's pure text parsers. Feeds sample
 * `/proc/net/arp` and `arp`-command output directly to [parseProcNetArp] /
 * [parseArpCommandOutput] — no real ARP cache, no subprocess — so the suite is
 * deterministic on any host (Linux CI, macOS dev box, Android host JVM).
 *
 * Lives in `jvmTest` (not `jvmSharedTest`): the resolver is `jvmMain`-only, since
 * Android cannot read the ARP cache.
 */
class JvmArpResolverTest {
    private val expectedMac =
        byteArrayOf(
            0xAA.toByte(),
            0xBB.toByte(),
            0xCC.toByte(),
            0xDD.toByte(),
            0xEE.toByte(),
            0xFF.toByte(),
        )

    // --- /proc/net/arp (Linux) ------------------------------------------------

    @Test
    fun parses_proc_net_arp_row() {
        val content =
            """
            IP address       HW type     Flags       HW address            Mask     Device
            192.168.1.5      0x1         0x2         aa:bb:cc:dd:ee:ff     *        eth0
            """.trimIndent()

        val outcome = parseProcNetArp(content, "192.168.1.5")

        val resolved = assertIs<ArpLookupOutcome.Resolved>(outcome)
        assertContentEquals(expectedMac, resolved.mac)
    }

    @Test
    fun proc_net_arp_incomplete_flag_is_NotFound() {
        // Flags 0x0 => entry exists but is incomplete (no MAC resolved yet).
        val content =
            """
            IP address       HW type     Flags       HW address            Mask     Device
            192.168.1.9      0x1         0x0         00:00:00:00:00:00     *        eth0
            """.trimIndent()

        assertIs<ArpLookupOutcome.NotFound>(parseProcNetArp(content, "192.168.1.9"))
    }

    @Test
    fun proc_net_arp_missing_ip_is_NotFound() {
        val content =
            """
            IP address       HW type     Flags       HW address            Mask     Device
            192.168.1.5      0x1         0x2         aa:bb:cc:dd:ee:ff     *        eth0
            """.trimIndent()

        assertIs<ArpLookupOutcome.NotFound>(parseProcNetArp(content, "10.0.0.99"))
    }

    @Test
    fun proc_net_arp_picks_the_matching_row() {
        val content =
            """
            IP address       HW type     Flags       HW address            Mask     Device
            192.168.1.5      0x1         0x2         11:22:33:44:55:66     *        eth0
            192.168.1.6      0x1         0x2         aa:bb:cc:dd:ee:ff     *        eth0
            """.trimIndent()

        val resolved = assertIs<ArpLookupOutcome.Resolved>(parseProcNetArp(content, "192.168.1.6"))
        assertContentEquals(expectedMac, resolved.mac)
    }

    // --- arp command (macOS / Linux) -----------------------------------------

    @Test
    fun parses_macos_arp_output() {
        val output = "? (192.168.1.5) at aa:bb:cc:dd:ee:ff on en0 ifscope [ethernet]"

        val resolved = assertIs<ArpLookupOutcome.Resolved>(parseArpCommandOutput(output, "192.168.1.5"))
        assertContentEquals(expectedMac, resolved.mac)
    }

    @Test
    fun parses_macos_arp_short_octets_with_zero_pad() {
        // BSD arp can emit non-zero-padded octets; they must normalize to bytes.
        val output = "? (192.168.1.5) at 1:2:3:4:5:6 on en0 ifscope [ethernet]"

        val resolved = assertIs<ArpLookupOutcome.Resolved>(parseArpCommandOutput(output, "192.168.1.5"))
        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5, 6), resolved.mac)
    }

    @Test
    fun parses_linux_arp_n_output() {
        val output = "192.168.1.5              ether   aa:bb:cc:dd:ee:ff   C                     eth0"

        val resolved = assertIs<ArpLookupOutcome.Resolved>(parseArpCommandOutput(output, "192.168.1.5"))
        assertContentEquals(expectedMac, resolved.mac)
    }

    @Test
    fun macos_no_entry_is_NotFound() {
        val output = "192.168.1.5 (192.168.1.5) -- no entry"

        assertIs<ArpLookupOutcome.NotFound>(parseArpCommandOutput(output, "192.168.1.5"))
    }

    @Test
    fun linux_incomplete_is_NotFound() {
        val output = "192.168.1.5              (incomplete)                              eth0"

        assertIs<ArpLookupOutcome.NotFound>(parseArpCommandOutput(output, "192.168.1.5"))
    }

    @Test
    fun arp_output_without_matching_ip_is_NotFound() {
        val output = "? (192.168.1.99) at aa:bb:cc:dd:ee:ff on en0 ifscope [ethernet]"

        assertIs<ArpLookupOutcome.NotFound>(parseArpCommandOutput(output, "192.168.1.5"))
    }
}
