/*
 * Outcome — conversion to and from `kotlin.Result`.
 *
 * `toOutcome` / `toResult` move between the two freely (an `Outcome` is a thin
 * wrapper). Kotlin-only (`@HiddenFromObjC`).
 *
 * There is deliberately no `outcomeCatching { }` (a `runCatching` twin). A
 * catch-everything block is the wrong tool around coroutine code: it captures
 * `CancellationException`, turning cancellation into an ordinary failure, and
 * the kotlinx.coroutines maintainers' position (kotlinx.coroutines#1814) is that
 * no catch-all variant is correct in general — a `CancellationException` neither
 * proves the current coroutine was cancelled (`Deferred.await` rethrows another
 * coroutine's) nor is safe to swallow when it wasn't (`Flow.emit` uses one to end
 * collection). Build failures from the specific exceptions you can handle:
 *
 *     try { … } catch (e: IOException) { Outcome.failure(MyException.Io(e)) }
 *
 * `runCatching { … }.toOutcome()` remains available for non-suspending code,
 * where the trade-off is explicit at the call site.
 */
@file:OptIn(ExperimentalObjCRefinement::class)

package com.happycodelucky.outcome

import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/** Wrap this `kotlin.Result` as an [Outcome]. */
@HiddenFromObjC
public fun <T> Result<T>.toOutcome(): Outcome<T> = Outcome(this, false)

/** The underlying `kotlin.Result`. */
@HiddenFromObjC
public fun <T> Outcome<T>.toResult(): Result<T> = result
