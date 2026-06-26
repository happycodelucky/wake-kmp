package com.happycodelucky.wake.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class Ipv4Test {
    @Test
    fun parses_limited_broadcast() {
        assertEquals(0xFFFFFFFFu, parseIpv4("255.255.255.255"))
    }

    @Test
    fun parses_zero() {
        assertEquals(0x00000000u, parseIpv4("0.0.0.0"))
    }

    @Test
    fun parses_subnet_directed_broadcast() {
        // 192.168.1.255 -> 0xC0A801FF in host bit order (octet 0 most significant).
        assertEquals(0xC0A801FFu, parseIpv4("192.168.1.255"))
    }

    @Test
    fun trims_whitespace() {
        assertEquals(0xC0A801FFu, parseIpv4("  192.168.1.255  "))
    }

    @Test
    fun rejects_wrong_octet_count() {
        assertNull(parseIpv4("192.168.1"))
        assertNull(parseIpv4("192.168.1.1.1"))
        assertNull(parseIpv4(""))
    }

    @Test
    fun rejects_out_of_range_octet() {
        assertNull(parseIpv4("256.0.0.1"))
        assertNull(parseIpv4("192.168.1.300"))
    }

    @Test
    fun rejects_non_numeric_and_empty_octets() {
        assertNull(parseIpv4("192.168.x.1"))
        assertNull(parseIpv4("192..1.1"))
        assertNull(parseIpv4("192.168.1."))
        assertNull(parseIpv4("-1.0.0.0"))
    }

    @Test
    fun host_to_network_order_reverses_bytes() {
        // 0xC0A801FF -> 0xFF01A8C0
        assertEquals(0xFF01A8C0u, hostToNetworkOrder(0xC0A801FFu))
        // Symmetric for the all-ones broadcast.
        assertEquals(0xFFFFFFFFu, hostToNetworkOrder(0xFFFFFFFFu))
    }

    @Test
    fun port_to_network_order_swaps_bytes() {
        // Port 9 -> 0x0900 in network order.
        assertEquals(0x0900u.toUShort(), portToNetworkOrder(9))
        // Port 7 -> 0x0700.
        assertEquals(0x0700u.toUShort(), portToNetworkOrder(7))
        // Port 4096 (0x1000) -> 0x0010.
        assertEquals(0x0010u.toUShort(), portToNetworkOrder(4096))
    }
}
