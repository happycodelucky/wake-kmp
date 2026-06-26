/*
 * :wake-testing — public, scriptable fake for the Wake interface.
 *
 * A black-box test double: it implements the public [Wake] contract, records
 * every [wake] call it receives, and returns a programmable [WakeResult] —
 * without opening a socket or sending a packet. Consumers inject it wherever
 * their code depends on a [Wake], then assert on what was requested.
 *
 * Wake has no `.shared` singleton, so there is nothing to install / restore:
 * construct a [FakeWake] and pass it in. (Contrast with sibling libraries whose
 * stateful singletons need a `withFake…` install helper.)
 */
@file:OptIn(ExperimentalObjCName::class)

package com.happycodelucky.wake.testing

import com.happycodelucky.wake.Wake
import com.happycodelucky.wake.WakeResult
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * A recorded [Wake.wake] invocation.
 *
 * @property mac the MAC string passed by the unit under test.
 * @property broadcastAddress the broadcast address passed.
 * @property port the port passed.
 */
public data class WakeCall(
    val mac: String,
    val broadcastAddress: String,
    val port: Int,
)

/**
 * Scriptable, recording fake [Wake] for tests.
 *
 * By default every [wake] call records its arguments and returns
 * [WakeResult.Success]. Override the outcome with [result] to exercise a
 * consumer's error handling:
 *
 * ```kotlin
 * val fake = FakeWake(result = WakeResult.NetworkError("no route to host"))
 * val sut = MyFeature(wake = fake)
 *
 * sut.wakeMyDesktop()
 *
 * assertEquals(1, fake.callCount)
 * assertEquals("AA:BB:CC:DD:EE:FF", fake.lastCall?.mac)
 * ```
 *
 * Thread-safe: the call counter and last-call snapshot are atomic, so the fake
 * may be driven from any dispatcher.
 *
 * In Swift the class reads as `FakeWake` with `wake(mac:broadcastAddress:port:)`,
 * `callCount`, `lastCall`, `calls`, and `reset()`.
 *
 * @param result the [WakeResult] every [wake] call returns. Defaults to
 *   [WakeResult.Success].
 */
@ObjCName(name = "WakeTestingFakeWake", swiftName = "FakeWake")
public class FakeWake(
    private val result: WakeResult = WakeResult.Success,
) : Wake {
    private val _callCount = atomic(0)
    private val _calls = atomic(emptyList<WakeCall>())

    /** Total number of [wake] calls received. */
    public val callCount: Int
        get() = _callCount.value

    /** Every [wake] call in the order received. */
    public val calls: List<WakeCall>
        get() = _calls.value

    /** The most recent [wake] call, or `null` if [wake] has never been called. */
    public val lastCall: WakeCall?
        get() = _calls.value.lastOrNull()

    /** `true` once [wake] has been called at least once. */
    public val wasCalled: Boolean
        get() = _callCount.value > 0

    override suspend fun wake(
        mac: String,
        broadcastAddress: String,
        port: Int,
    ): WakeResult {
        _callCount.incrementAndGet()
        _calls.update { it + WakeCall(mac, broadcastAddress, port) }
        return result
    }

    /** Clear all recorded calls. Useful between assertion blocks. */
    public fun reset() {
        _callCount.value = 0
        _calls.value = emptyList()
    }
}
