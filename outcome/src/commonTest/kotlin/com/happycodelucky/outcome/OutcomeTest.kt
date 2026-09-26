package com.happycodelucky.outcome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [Outcome] must behave exactly like `kotlin.Result`; each case checks the
 * Outcome operation against the stdlib one it delegates to.
 */
class OutcomeTest {
    private val boom = IllegalStateException("boom")

    @Test
    fun success_exposes_its_value() {
        val outcome = Outcome.success(42)

        assertTrue(outcome.isSuccess)
        assertFalse(outcome.isFailure)
        assertEquals(42, outcome.getOrNull())
        assertEquals(42, outcome.getOrThrow())
        assertNull(outcome.exceptionOrNull())
    }

    @Test
    fun failure_exposes_its_exception() {
        val outcome = Outcome.failure<Int>(boom)

        assertTrue(outcome.isFailure)
        assertFalse(outcome.isSuccess)
        assertNull(outcome.getOrNull())
        assertSame(boom, outcome.exceptionOrNull())
        assertSame(boom, assertFailsWith<IllegalStateException> { outcome.getOrThrow() })
    }

    @Test
    fun a_null_success_is_still_a_success() {
        val outcome = Outcome.success<String?>(null)

        assertTrue(outcome.isSuccess)
        assertNull(outcome.getOrNull())
    }

    @Test
    fun converts_to_and_from_kotlin_Result() {
        val result = Result.success("x")

        assertEquals(result, result.toOutcome().toResult())
        assertSame(boom, Result.failure<Int>(boom).toOutcome().exceptionOrNull())
    }

    @Test
    fun equality_and_toString_follow_kotlin_Result() {
        assertEquals(Outcome.success(1), Outcome.success(1))
        assertEquals(Outcome.success(1).hashCode(), Outcome.success(1).hashCode())
        assertEquals(Result.success(1).toString(), Outcome.success(1).toString())
        assertEquals(Result.failure<Int>(boom).toString(), Outcome.failure<Int>(boom).toString())
    }

    @Test
    fun map_and_fold() {
        assertEquals(Outcome.success(4), Outcome.success(2).map { it * 2 })
        assertSame(boom, Outcome.failure<Int>(boom).map { it * 2 }.exceptionOrNull())
        assertEquals("2", Outcome.success(2).fold({ it.toString() }, { "failed" }))
        assertEquals("failed", Outcome.failure<Int>(boom).fold({ it.toString() }, { "failed" }))
    }

    @Test
    fun mapCatching_captures_a_throwing_transform() {
        val outcome = Outcome.success(2).mapCatching { throw boom }

        assertSame(boom, outcome.exceptionOrNull())
    }

    @Test
    fun getOrElse_and_getOrDefault() {
        assertEquals(1, Outcome.success(1).getOrElse { 0 })
        assertEquals(0, Outcome.failure<Int>(boom).getOrElse { 0 })
        assertEquals(0, Outcome.failure<Int>(boom).getOrDefault(0))
    }

    @Test
    fun recover_turns_a_failure_into_a_success() {
        assertEquals(Outcome.success(0), Outcome.failure<Int>(boom).recover { 0 })
        assertEquals(Outcome.success(1), Outcome.success(1).recover { 0 })
        assertIs<UnsupportedOperationException>(
            Outcome.failure<Int>(boom).recoverCatching { throw UnsupportedOperationException() }.exceptionOrNull(),
        )
    }

    @Test
    fun onSuccess_and_onFailure_run_only_on_their_branch() {
        val seen = mutableListOf<String>()

        Outcome.success(1).onSuccess { seen += "success $it" }.onFailure { seen += "failure" }
        Outcome.failure<Int>(boom).onSuccess { seen += "success" }.onFailure { seen += "failure ${it.message}" }

        assertEquals(listOf("success 1", "failure boom"), seen)
    }

    @Test
    fun outcomeCatching_captures_value_or_exception() {
        assertEquals(Outcome.success(3), outcomeCatching { 1 + 2 })
        assertSame(boom, outcomeCatching { throw boom }.exceptionOrNull())
        assertEquals(Outcome.success(5), "hello".outcomeCatching { length })
    }
}
