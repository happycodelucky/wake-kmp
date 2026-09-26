/*
 * Wake — the failure half of `Wake.up`'s `Result` (CLAUDE.md §8).
 *
 * A sealed `Exception` hierarchy rather than a sealed result type: it is what
 * `kotlin.Result` carries, so Kotlin consumers get the standard `Result` API
 * with a closed, exhaustively-matchable error set. On the Swift side SKIE
 * renders the sealed class for `onEnum(of:)`, which the bundled Swift uses to
 * map each case onto the native `WakeError` enum.
 */
package com.happycodelucky.wake

/**
 * Why a [Wake.up] failed. Always the exception inside a failed
 * `Result<Unit>` from [Wake.up] / [WakeSender.up].
 *
 * ```kotlin
 * val error = Wake.up(mac).exceptionOrNull() as? WakeException
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
