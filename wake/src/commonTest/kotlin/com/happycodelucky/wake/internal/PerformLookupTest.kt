package com.happycodelucky.wake.internal

import com.happycodelucky.wake.MacLookupException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * White-box test of [performLookup]'s orchestration. Uses an in-test recording
 * [ArpResolver] (visible because it is `internal` to `:wake` and this is the
 * `:wake` test source set) to assert the validate → read → map chain without a
 * real OS ARP read.
 *
 * The per-platform `lookupMac` entry points (macosMain / jvmMain) simply call
 * [performLookup] with the platform resolver, so once this passes and each
 * resolver returns the right outcome, the public entry points are covered too.
 */
class PerformLookupTest {
    /** Records the requested IP and returns a programmable outcome. */
    private class RecordingResolver(
        private val outcome: ArpLookupOutcome = ArpLookupOutcome.NotFound,
    ) : ArpResolver {
        var requestedIp: String? = null
        var resolveCount: Int = 0

        override suspend fun resolve(ipv4: String): ArpLookupOutcome {
            resolveCount++
            requestedIp = ipv4
            return outcome
        }
    }

    @Test
    fun resolved_succeeds_with_formatted_mac() =
        runTest {
            val mac =
                byteArrayOf(
                    0xAA.toByte(),
                    0xBB.toByte(),
                    0xCC.toByte(),
                    0xDD.toByte(),
                    0xEE.toByte(),
                    0xFF.toByte(),
                )
            val resolver = RecordingResolver(ArpLookupOutcome.Resolved(mac))

            val result = performLookup(resolver, "192.168.1.42")

            assertEquals("AA:BB:CC:DD:EE:FF", result.getOrThrow())
        }

    @Test
    fun not_found_fails_with_NotInCache() =
        runTest {
            val resolver = RecordingResolver(ArpLookupOutcome.NotFound)

            val result = performLookup(resolver, "192.168.1.42")

            val error = assertIs<MacLookupException.NotInCache>(result.exceptionOrNull())
            assertEquals("192.168.1.42", error.ip)
        }

    @Test
    fun failed_fails_with_LookupFailed() =
        runTest {
            val resolver = RecordingResolver(ArpLookupOutcome.Failed("sysctl failed, rc=-1"))

            val result = performLookup(resolver, "192.168.1.42")

            val error = assertIs<MacLookupException.LookupFailed>(result.exceptionOrNull())
            assertEquals("sysctl failed, rc=-1", error.message)
        }

    @Test
    fun invalid_ip_fails_with_InvalidIpAddress_and_never_reads() =
        runTest {
            val resolver = RecordingResolver(ArpLookupOutcome.Resolved(ByteArray(MAC_LENGTH)))

            val result = performLookup(resolver, "not-an-ip")

            assertIs<MacLookupException.InvalidIpAddress>(result.exceptionOrNull())
            assertEquals(0, resolver.resolveCount)
            assertNull(resolver.requestedIp)
        }

    @Test
    fun valid_ip_passes_through_to_resolver() =
        runTest {
            val resolver = RecordingResolver()

            performLookup(resolver, "10.0.0.1")

            assertEquals(1, resolver.resolveCount)
            assertEquals("10.0.0.1", resolver.requestedIp)
        }
}
