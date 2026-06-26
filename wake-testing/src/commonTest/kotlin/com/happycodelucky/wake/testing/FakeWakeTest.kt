package com.happycodelucky.wake.testing

import com.happycodelucky.wake.DEFAULT_BROADCAST_ADDRESS
import com.happycodelucky.wake.DEFAULT_WAKE_PORT
import com.happycodelucky.wake.WakeResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeWakeTest {
    @Test
    fun defaults_to_Success_and_records_the_call() =
        runTest {
            val fake = FakeWake()

            val result = fake.wake("AA:BB:CC:DD:EE:FF")

            assertIs<WakeResult.Success>(result)
            assertTrue(fake.wasCalled)
            assertEquals(1, fake.callCount)
            assertEquals("AA:BB:CC:DD:EE:FF", fake.lastCall?.mac)
            assertEquals(DEFAULT_BROADCAST_ADDRESS, fake.lastCall?.broadcastAddress)
            assertEquals(DEFAULT_WAKE_PORT, fake.lastCall?.port)
        }

    @Test
    fun returns_the_programmed_result() =
        runTest {
            val fake = FakeWake(result = WakeResult.NetworkError("no route to host"))

            val result = fake.wake("AABBCCDDEEFF")

            val error = assertIs<WakeResult.NetworkError>(result)
            assertEquals("no route to host", error.message)
        }

    @Test
    fun records_every_call_in_order() =
        runTest {
            val fake = FakeWake()

            fake.wake("AA:BB:CC:DD:EE:01", broadcastAddress = "192.168.1.255", port = 7)
            fake.wake("AA:BB:CC:DD:EE:02")

            assertEquals(2, fake.callCount)
            assertEquals(
                listOf(
                    WakeCall("AA:BB:CC:DD:EE:01", "192.168.1.255", 7),
                    WakeCall("AA:BB:CC:DD:EE:02", DEFAULT_BROADCAST_ADDRESS, DEFAULT_WAKE_PORT),
                ),
                fake.calls,
            )
        }

    @Test
    fun starts_empty() {
        val fake = FakeWake()
        assertFalse(fake.wasCalled)
        assertEquals(0, fake.callCount)
        assertNull(fake.lastCall)
        assertTrue(fake.calls.isEmpty())
    }

    @Test
    fun reset_clears_recorded_calls() =
        runTest {
            val fake = FakeWake()
            fake.wake("AABBCCDDEEFF")

            fake.reset()

            assertFalse(fake.wasCalled)
            assertEquals(0, fake.callCount)
            assertNull(fake.lastCall)
        }
}
