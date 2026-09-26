/*
 * Outcome — a Swift-friendly `kotlin.Result`.
 *
 * Why not `kotlin.Result` itself: it is a value class, and ObjC export erases a
 * value class to its underlying type — `Result<T>` reaches Swift as an untyped
 * `Any?` (KT-32352). Nor can a platform's native type sit under an
 * `expect class Result`: `actual typealias = kotlin.Result` is a compile error
 * (`Result<out T>` has declaration-site variance), and Swift's `Result` is not
 * reachable from Kotlin/Native at all.
 *
 * So [Outcome] is an ordinary (reference) class that *wraps* a `kotlin.Result`
 * and forwards to it: Kotlin gets the stdlib `Result` API and semantics verbatim
 * (see OutcomeExtensions.kt, and [toResult] / [toOutcome] to convert), and Swift
 * gets a real class it can hold.
 *
 * The Swift side is written once, in `src/appleMain/swift/Outcome+Swift.swift`,
 * which SKIE's Swift bundling compiles into every framework that `export`s this
 * module. It adds `try outcome.get()` (the value bridged to a Swift type, or the
 * Kotlin exception thrown as a Swift `Error`) and `outcome.result(as:)`
 * (`Swift.Result`). No per-function or per-error Swift is needed: a library
 * returns `Outcome<T>` from commonMain and throws/matches its own sealed
 * exception hierarchy, which SKIE renders for `onEnum(of:)`.
 */
@file:OptIn(ExperimentalObjCRefinement::class)

package com.happycodelucky.outcome

import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.native.ShouldRefineInSwift

/**
 * A discriminated union of a successful value of type [T] or a failure
 * [Throwable] — `kotlin.Result`, in a form Swift can consume.
 *
 * Every operation delegates to an underlying `kotlin.Result`, so behavior is the
 * stdlib's exactly. The transforming operators (`map`, `fold`, `onFailure`, …)
 * are extensions with the stdlib's signatures; [toResult] returns the underlying
 * `kotlin.Result` when an API wants the stdlib type.
 *
 * ```kotlin
 * fun parse(text: String): Outcome<Int> =
 *     text.toIntOrNull()?.let { Outcome.success(it) }
 *         ?: Outcome.failure(IllegalArgumentException("not a number: $text"))
 *
 * parse("42").map { it * 2 }.getOrDefault(0) // 84
 * ```
 *
 * In Swift, unwrap with the bundled `get()`:
 *
 * ```swift
 * let n: Int = try parse(text: "42").get()
 * ```
 */
public class Outcome<out T>
    @PublishedApi
    internal constructor(
        @PublishedApi internal val result: Result<T>,
    ) {
        /** `true` if this is a success. The opposite of [isFailure]. */
        public val isSuccess: Boolean
            get() = result.isSuccess

        /** `true` if this is a failure. The opposite of [isSuccess]. */
        public val isFailure: Boolean
            get() = result.isFailure

        /** The success value, or `null` on failure. Same as `Result.getOrNull`. */
        public fun getOrNull(): T? = result.getOrNull()

        /** The failure's exception, or `null` on success. Same as `Result.exceptionOrNull`. */
        public fun exceptionOrNull(): Throwable? = result.exceptionOrNull()

        /**
         * The success value, or throws the failure's exception. Same as
         * `Result.getOrThrow`.
         *
         * Hidden from Swift, where an undeclared Kotlin throw would abort; Swift
         * uses the bundled `get()` instead.
         */
        @HiddenFromObjC
        public fun getOrThrow(): T = result.getOrThrow()

        /**
         * The success value with its static type erased, for the bundled Swift.
         *
         * Swift extensions of a generic ObjC class cannot touch its generic
         * parameter, so the bundled `get<V>()` reads this untyped `Any?` (exposed as
         * `__anyValue`) and bridges it with `as? V`. Kotlin callers use [getOrNull].
         */
        @InternalOutcomeApi
        @ShouldRefineInSwift
        public val anyValue: Any?
            get() = result.getOrNull()

        override fun equals(other: Any?): Boolean = other is Outcome<*> && other.result == result

        override fun hashCode(): Int = result.hashCode()

        /** `Success(v)` or `Failure(x)`, as `kotlin.Result` prints. */
        override fun toString(): String = result.toString()

        /** Constructors, mirroring `Result.success` / `Result.failure`. */
        @HiddenFromObjC
        public companion object {
            /** A successful [Outcome] holding [value]. */
            public fun <T> success(value: T): Outcome<T> = Outcome(Result.success(value))

            /** A failed [Outcome] holding [exception]. */
            public fun <T> failure(exception: Throwable): Outcome<T> = Outcome(Result.failure(exception))
        }
    }

/**
 * Marks [Outcome] members that exist only for the bundled Swift. Not for Kotlin
 * callers — using one is a compile error unless explicitly opted in.
 */
@RequiresOptIn(
    message = "Swift-bridge plumbing for Outcome's bundled Swift. Kotlin callers should use the Result-style API.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
public annotation class InternalOutcomeApi
