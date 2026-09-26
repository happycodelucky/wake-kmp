/*
 * Wake — opt-in gate for the Swift-bridge entry points.
 *
 * `kotlin.Result` is a value class, which ObjC export erases to `Any?`, so each
 * `Result`-returning API is `@HiddenFromObjC` and paired with a throwing twin
 * that the bundled Swift (`src/appleMain/swift/`, `src/macosMain/swift/`) wraps. Those twins must be
 * `public` to be exported, but they are plumbing: this annotation makes calling
 * one from Kotlin a compile error unless explicitly opted in.
 */
package com.happycodelucky.wake

/**
 * Marks a Swift-bridge entry point. Not for Kotlin callers — use the
 * `Result`-returning API it mirrors (e.g. [Wake.up] rather than
 * [Wake.upOrThrow]).
 */
@RequiresOptIn(
    message = "Swift-bridge plumbing. Kotlin callers should use the Result-returning API instead.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
public annotation class InternalWakeSwiftApi
