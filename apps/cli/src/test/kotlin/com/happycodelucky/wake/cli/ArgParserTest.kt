package com.happycodelucky.wake.cli

import com.happycodelucky.wake.DEFAULT_BROADCAST_ADDRESS
import com.happycodelucky.wake.DEFAULT_WAKE_PORT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ArgParserTest {
    @Test
    fun bare_mac_is_WakeByMac_with_defaults() {
        val command = assertIs<CliCommand.WakeByMac>(parseArgs(arrayOf("AA:BB:CC:DD:EE:FF")))
        assertEquals(listOf("AA:BB:CC:DD:EE:FF"), command.macs)
        assertEquals(DEFAULT_BROADCAST_ADDRESS, command.broadcast)
        assertEquals(DEFAULT_WAKE_PORT, command.port)
    }

    @Test
    fun multiple_macs_are_collected_in_order() {
        val command =
            assertIs<CliCommand.WakeByMac>(
                parseArgs(arrayOf("AA:BB:CC:DD:EE:FF", "11:22:33:44:55:66")),
            )
        assertEquals(listOf("AA:BB:CC:DD:EE:FF", "11:22:33:44:55:66"), command.macs)
    }

    @Test
    fun multiple_macs_share_broadcast_and_port() {
        val command =
            assertIs<CliCommand.WakeByMac>(
                parseArgs(arrayOf("AABBCCDDEEFF", "112233445566", "--port", "7", "--broadcast", "192.168.1.255")),
            )
        assertEquals(listOf("AABBCCDDEEFF", "112233445566"), command.macs)
        assertEquals("192.168.1.255", command.broadcast)
        assertEquals(7, command.port)
    }

    @Test
    fun ip_flag_is_WakeByIp() {
        val command = assertIs<CliCommand.WakeByIp>(parseArgs(arrayOf("--ip", "192.168.1.5")))
        assertEquals("192.168.1.5", command.ip)
        assertEquals(DEFAULT_BROADCAST_ADDRESS, command.broadcast)
        assertEquals(DEFAULT_WAKE_PORT, command.port)
    }

    @Test
    fun broadcast_and_port_override() {
        val command =
            assertIs<CliCommand.WakeByMac>(
                parseArgs(arrayOf("AABBCCDDEEFF", "--broadcast", "192.168.1.255", "--port", "7")),
            )
        assertEquals(listOf("AABBCCDDEEFF"), command.macs)
        assertEquals("192.168.1.255", command.broadcast)
        assertEquals(7, command.port)
    }

    @Test
    fun flags_may_precede_the_mac() {
        val command =
            assertIs<CliCommand.WakeByMac>(
                parseArgs(arrayOf("--port", "9", "AA:BB:CC:DD:EE:FF")),
            )
        assertEquals(listOf("AA:BB:CC:DD:EE:FF"), command.macs)
        assertEquals(9, command.port)
    }

    @Test
    fun help_anywhere_is_ShowHelp() {
        assertIs<CliCommand.ShowHelp>(parseArgs(arrayOf("--help")))
        assertIs<CliCommand.ShowHelp>(parseArgs(arrayOf("AA:BB:CC:DD:EE:FF", "--help")))
        assertIs<CliCommand.ShowHelp>(parseArgs(arrayOf("-h")))
    }

    @Test
    fun mac_and_ip_together_is_BadUsage() {
        assertIs<CliCommand.BadUsage>(parseArgs(arrayOf("AA:BB:CC:DD:EE:FF", "--ip", "192.168.1.5")))
    }

    @Test
    fun neither_mac_nor_ip_is_BadUsage() {
        assertIs<CliCommand.BadUsage>(parseArgs(arrayOf<String>()))
        assertIs<CliCommand.BadUsage>(parseArgs(arrayOf("--port", "9")))
    }

    @Test
    fun flag_missing_value_is_BadUsage() {
        assertIs<CliCommand.BadUsage>(parseArgs(arrayOf("--ip")))
        assertIs<CliCommand.BadUsage>(parseArgs(arrayOf("--port")))
        assertIs<CliCommand.BadUsage>(parseArgs(arrayOf("--broadcast")))
    }

    @Test
    fun non_numeric_or_out_of_range_port_is_BadUsage() {
        assertIs<CliCommand.BadUsage>(parseArgs(arrayOf("AA:BB:CC:DD:EE:FF", "--port", "abc")))
        assertIs<CliCommand.BadUsage>(parseArgs(arrayOf("AA:BB:CC:DD:EE:FF", "--port", "70000")))
        assertIs<CliCommand.BadUsage>(parseArgs(arrayOf("AA:BB:CC:DD:EE:FF", "--port", "-1")))
    }

    @Test
    fun unknown_flag_is_BadUsage() {
        assertIs<CliCommand.BadUsage>(parseArgs(arrayOf("--wat")))
        assertIs<CliCommand.BadUsage>(parseArgs(arrayOf("AA:BB:CC:DD:EE:FF", "--frobnicate")))
    }

    @Test
    fun port_does_not_consume_a_following_mac() {
        // Regression guard: a valid bare MAC after a complete --port must still
        // be treated as the positional MAC, not swallowed by --port.
        val command =
            assertIs<CliCommand.WakeByMac>(
                parseArgs(arrayOf("--port", "9", "--broadcast", "10.0.0.255", "AABBCCDDEEFF")),
            )
        assertEquals(listOf("AABBCCDDEEFF"), command.macs)
        assertEquals("10.0.0.255", command.broadcast)
        assertEquals(9, command.port)
    }
}
