/*
 * Wake — the failure half of `Wake.up`'s `Outcome` (CLAUDE.md §8).
 *
 * A sealed `Exception` hierarchy rather than a sealed result type: it is what an
 * `Outcome` / `kotlin.Result` carries, so Kotlin consumers get the standard
 * `Result` API with a closed, exhaustively-matchable error set. Swift catches it
 * as itself (`catch let e as WakeException`) — `:outcome`'s bundled Swift throws
 * Kotlin exceptions as Swift errors — and switches with SKIE's `onEnum(of:)`.
 */
package com.happycodelucky.wake

/**
 * Why a [Wake.up] failed. Always the exception inside a failed
 * `Outcome<Unit>` from [Wake.up] / [WakeSender.up].
 *
 * ```kotlin
 * val error = Wake.up(mac).exceptionOrNull() as? WakeException
 * ```
 *
 * ```swift
 * #expect(throws: WakeException.InvalidMacAddress.self) {
 *     try await Wake.up(mac: "zz").get()
 * }
 * ```
 */
public sealed class WakeException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /** Non-null: every [WakeException] is constructed with a message. */
    override val message: String
        get() = super.message.orEmpty()

    /**
     * The MAC string could not be parsed into a 48-bit address.
     *
     * @property mac the input that failed to parse, verbatim.
     */
    public class InvalidMacAddress(
        public val mac: String,
    ) : WakeException("could not parse MAC address: \"$mac\"")

    /**
     * The underlying socket send failed (e.g. the broadcast address was
     * unresolvable, the socket could not be opened, or `sendto` returned an
     * error). [message] is the platform error message or errno description;
     * [cause] is the platform exception where there was one (JVM / Android).
     */
    public class NetworkError(
        message: String,
        cause: Throwable? = null,
    ) : WakeException(message, cause)
}
