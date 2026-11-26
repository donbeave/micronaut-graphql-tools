package io.micronaut.graphql.tools.testtimeout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class RunningTestsTest {
    private var now = 0L
    private val tests = RunningTests(Duration.ofSeconds(10)) { now }

    @Test
    fun `a test under the limit is not overdue`() {
        tests.started("a", "ASpec > feature")
        now = Duration.ofSeconds(10).toNanos()

        assertTrue(tests.takeOverdue().isEmpty())
    }

    @Test
    fun `a test over the limit is named, with its time`() {
        tests.started("a", "ASpec > feature")
        now = Duration.ofSeconds(11).toNanos()

        val overdue = tests.takeOverdue().single()
        assertEquals("ASpec > feature", overdue.name)
        assertEquals(Duration.ofSeconds(11), overdue.running)
    }

    @Test
    fun `a finished test is not overdue`() {
        tests.started("a", "ASpec > feature")
        tests.finished("a")
        now = Duration.ofMinutes(5).toNanos()

        assertTrue(tests.takeOverdue().isEmpty())
    }

    @Test
    fun `an overdue test is reported once`() {
        tests.started("a", "ASpec > feature")
        now = Duration.ofSeconds(11).toNanos()

        assertEquals(1, tests.takeOverdue().size)
        assertTrue(tests.takeOverdue().isEmpty())
    }
}
