/*
 * Outcome — the `kotlin.Result` operator set, verbatim.
 *
 * Each function has the stdlib `Result` extension's name and signature and
 * delegates to it, so moving code between `Result` and `Outcome` is a type change
 * only. All are `@HiddenFromObjC`: most take Kotlin lambdas, which bridge
 * poorly, and Swift converts once with `result(as:)` (bundled Swift) and then
 * uses `Swift.Result`'s own operators.
 */
@file:OptIn(ExperimentalObjCRefinement::class)

package com.happycodelucky.outcome

import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/** The value, or [onFailure]'s result for the exception. Same as `Result.getOrElse`. */
@HiddenFromObjC
public inline fun <R, T : R> Outcome<T>.getOrElse(onFailure: (exception: Throwable) -> R): R =
    result.getOrElse(onFailure)

/** The value, or [defaultValue] on failure. Same as `Result.getOrDefault`. */
@HiddenFromObjC
public fun <R, T : R> Outcome<T>.getOrDefault(defaultValue: R): R = result.getOrDefault(defaultValue)

/** [onSuccess] of the value or [onFailure] of the exception. Same as `Result.fold`. */
@HiddenFromObjC
public inline fun <R, T> Outcome<T>.fold(
    onSuccess: (value: T) -> R,
    onFailure: (exception: Throwable) -> R,
): R = result.fold(onSuccess, onFailure)

/** Transform the value; a failure passes through. Same as `Result.map`. */
@HiddenFromObjC
public inline fun <R, T> Outcome<T>.map(transform: (value: T) -> R): Outcome<R> = result.map(transform).toOutcome()

/** Like [map], but an exception from [transform] becomes a failure. Same as `Result.mapCatching`. */
@HiddenFromObjC
public inline fun <R, T> Outcome<T>.mapCatching(transform: (value: T) -> R): Outcome<R> =
    result.mapCatching(transform).toOutcome()

/** Turn a failure into a success; a success passes through. Same as `Result.recover`. */
@HiddenFromObjC
public inline fun <R, T : R> Outcome<T>.recover(transform: (exception: Throwable) -> R): Outcome<R> =
    result.recover(transform).toOutcome()

/** Like [recover], but an exception from [transform] becomes a failure. Same as `Result.recoverCatching`. */
@HiddenFromObjC
public inline fun <R, T : R> Outcome<T>.recoverCatching(transform: (exception: Throwable) -> R): Outcome<R> =
    result.recoverCatching(transform).toOutcome()

/** Run [action] on the exception if this is a failure; returns this. Same as `Result.onFailure`. */
@HiddenFromObjC
public inline fun <T> Outcome<T>.onFailure(action: (exception: Throwable) -> Unit): Outcome<T> {
    result.onFailure(action)
    return this
}

/** Run [action] on the value if this is a success; returns this. Same as `Result.onSuccess`. */
@HiddenFromObjC
public inline fun <T> Outcome<T>.onSuccess(action: (value: T) -> Unit): Outcome<T> {
    result.onSuccess(action)
    return this
}
