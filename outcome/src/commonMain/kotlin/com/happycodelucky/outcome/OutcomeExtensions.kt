/*
 * Outcome — the `kotlin.Result` operator set, verbatim.
 *
 * Each function has the stdlib `Result` extension's name and signature and
 * delegates to it, so moving code between `Result` and `Outcome` is a type change
 * only. All are `@HiddenFromObjC`: most take Kotlin lambdas, which bridge
 * poorly, and Swift converts once with `result(as:)` (bundled Swift) and then
 * uses `Swift.Result`'s own operators.
 *
 * ExperimentalContracts opt-in: `callsInPlace` is declared exactly where the
 * stdlib `Result` operators declare it (getOrElse, fold, map, recover,
 * onSuccess, onFailure — not the *Catching ones), so Kotlin's flow analysis
 * treats `Outcome` lambdas like `Result` ones. Rollback: delete the `contract`
 * blocks.
 */
@file:OptIn(ExperimentalObjCRefinement::class, ExperimentalContracts::class)

package com.happycodelucky.outcome

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/** The value, or [onFailure]'s result for the exception. Same as `Result.getOrElse`. */
@HiddenFromObjC
public inline fun <R, T : R> Outcome<T>.getOrElse(onFailure: (exception: Throwable) -> R): R {
    contract { callsInPlace(onFailure, InvocationKind.AT_MOST_ONCE) }
    return result.getOrElse(onFailure)
}

/** The value, or [defaultValue] on failure. Same as `Result.getOrDefault`. */
@HiddenFromObjC
public fun <R, T : R> Outcome<T>.getOrDefault(defaultValue: R): R = result.getOrDefault(defaultValue)

/** [onSuccess] of the value or [onFailure] of the exception. Same as `Result.fold`. */
@HiddenFromObjC
public inline fun <R, T> Outcome<T>.fold(
    onSuccess: (value: T) -> R,
    onFailure: (exception: Throwable) -> R,
): R {
    contract {
        callsInPlace(onSuccess, InvocationKind.AT_MOST_ONCE)
        callsInPlace(onFailure, InvocationKind.AT_MOST_ONCE)
    }
    return result.fold(onSuccess, onFailure)
}

/** Transform the value; a failure passes through. Same as `Result.map`. */
@HiddenFromObjC
public inline fun <R, T> Outcome<T>.map(transform: (value: T) -> R): Outcome<R> {
    contract { callsInPlace(transform, InvocationKind.AT_MOST_ONCE) }
    return result.map(transform).toOutcome()
}

/**
 * Like [map], but an exception from [transform] becomes a failure. Same as
 * `Result.mapCatching` — including that it captures *every* exception, so don't
 * call suspending code in [transform] (a cancellation would become a failure).
 */
@HiddenFromObjC
public inline fun <R, T> Outcome<T>.mapCatching(transform: (value: T) -> R): Outcome<R> =
    result.mapCatching(transform).toOutcome()

/** Turn a failure into a success; a success passes through. Same as `Result.recover`. */
@HiddenFromObjC
public inline fun <R, T : R> Outcome<T>.recover(transform: (exception: Throwable) -> R): Outcome<R> {
    contract { callsInPlace(transform, InvocationKind.AT_MOST_ONCE) }
    return result.recover(transform).toOutcome()
}

/**
 * Like [recover], but an exception from [transform] becomes a failure. Same as
 * `Result.recoverCatching` — and, like it, captures every exception: keep
 * suspending code out of [transform].
 */
@HiddenFromObjC
public inline fun <R, T : R> Outcome<T>.recoverCatching(transform: (exception: Throwable) -> R): Outcome<R> =
    result.recoverCatching(transform).toOutcome()

/** Run [action] on the exception if this is a failure; returns this. Same as `Result.onFailure`. */
@HiddenFromObjC
public inline fun <T> Outcome<T>.onFailure(action: (exception: Throwable) -> Unit): Outcome<T> {
    contract { callsInPlace(action, InvocationKind.AT_MOST_ONCE) }
    result.onFailure(action)
    return this
}

/** Run [action] on the value if this is a success; returns this. Same as `Result.onSuccess`. */
@HiddenFromObjC
public inline fun <T> Outcome<T>.onSuccess(action: (value: T) -> Unit): Outcome<T> {
    contract { callsInPlace(action, InvocationKind.AT_MOST_ONCE) }
    result.onSuccess(action)
    return this
}
