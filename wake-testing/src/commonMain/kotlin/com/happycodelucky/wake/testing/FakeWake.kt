/*
 * :wake-testing — public, scriptable fake for the Wake send seam.
 *
 * `Wake` is a stateless `object` whose `up(...)` is a static call, so it cannot
 * be a supertype and a fake can no longer "be a Wake". The injectable contract
 * is [com.happycodelucky.wake.WakeSender] — a tiny interface a feature depends
 * on instead of reaching for `Wake.up` directly; production wires
 * `Wake.asSender()`, tests wire this [FakeWake].
 *
 * [FakeWake] is a black-box double: it records every [up] call it receives and
 * returns a programmable `Result` without opening a socket or sending a
 * packet. Construct one, inject it where your code expects a [WakeSender], then
 * assert on what was requested. There is nothing to install or restore.
 *
 * Kotlin-only (`@HiddenFromObjC`), like the [com.happycodelucky.wake.WakeSender]
 * seam it implements.
 */
@file:OptIn(ExperimentalObjCRefinement::class)

package com.happycodelucky.wake.testing

import com.happycodelucky.wake.DEFAULT_BROADCAST_ADDRESS
import com.happycodelucky.wake.DEFAULT_WAKE_PORT
import com.happycodelucky.wake.WakeSender
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * A recorded [WakeSender.up] invocation.
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
 * Scriptable, recording [WakeSender] for tests.
 *
 * By default every [up] call records its arguments and succeeds. Override the
 * outcome with [result] to exercise a consumer's error handling:
 *
 * ```kotlin
 * val fake = FakeWake(result = Result.failure(WakeException.NetworkError("no route to host")))
 * val sut = MyFeature(wake = fake) // MyFeature depends on WakeSender, not Wake
 *
 * sut.wakeMyDesktop()
 *
 * assertEquals(1, fake.callCount)
 * assertEquals("AA:BB:CC:DD:EE:FF", fake.lastCall?.mac)
 * ```
 *
 * Thread-safe: the call counter and recorded-call list are atomic, so the fake
 * may be driven from any dispatcher.
 *
 * @param result the `Result` every [up] call returns. Defaults to success; a
 *   failure should hold a [com.happycodelucky.wake.WakeException], matching the
 *   real [com.happycodelucky.wake.Wake.up] contract.
 */
@HiddenFromObjC
public class FakeWake(
    private val result: Result<Unit> = Result.success(Unit),
) : WakeSender {
    private val _callCount = atomic(0)
    private val _calls = atomic(emptyList<WakeCall>())

    /** Total number of [up] calls received. */
    public val callCount: Int
        get() = _callCount.value

    /** Every [up] call in the order received. */
    public val calls: List<WakeCall>
        get() = _calls.value

    /** The most recent [up] call, or `null` if [up] has never been called. */
    public val lastCall: WakeCall?
        get() = _calls.value.lastOrNull()

    /** `true` once [up] has been called at least once. */
    public val wasCalled: Boolean
        get() = _callCount.value > 0

    override suspend fun up(
        mac: String,
        broadcastAddress: String,
        port: Int,
    ): Result<Unit> {
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
