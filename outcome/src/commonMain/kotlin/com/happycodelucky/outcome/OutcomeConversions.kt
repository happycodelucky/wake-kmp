/*
 * Outcome — construction from, and conversion to, `kotlin.Result`.
 *
 * `toOutcome` / `toResult` move between the two freely (an `Outcome` is a thin
 * wrapper), and `outcomeCatching` mirrors `runCatching` exactly — including
 * catching `CancellationException`, as `runCatching` does, so don't wrap a
 * suspending call you expect to be cancellable. Kotlin-only (`@HiddenFromObjC`).
 */
@file:OptIn(ExperimentalObjCRefinement::class)

package com.happycodelucky.outcome

import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/** Wrap this `kotlin.Result` as an [Outcome]. */
@HiddenFromObjC
public fun <T> Result<T>.toOutcome(): Outcome<T> = Outcome(this)

/** The underlying `kotlin.Result`. */
@HiddenFromObjC
public fun <T> Outcome<T>.toResult(): Result<T> = result

/** Run [block], capturing its value or any thrown exception. Same as `runCatching`. */
@HiddenFromObjC
public inline fun <R> outcomeCatching(block: () -> R): Outcome<R> = runCatching(block).toOutcome()

/** Run [block] on this receiver, capturing its value or exception. Same as `T.runCatching`. */
@HiddenFromObjC
public inline fun <T, R> T.outcomeCatching(block: T.() -> R): Outcome<R> = runCatching(block).toOutcome()
