package io.micronaut.graphql.tools.testtimeout

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/** The tests that run now, and which of them ran longer than [limit]. */
class RunningTests(private val limit: Duration, private val nanoTime: () -> Long = System::nanoTime) {
    data class Overdue(val name: String, val running: Duration)

    private class Entry(val name: String, val startedAt: Long)

    private val running = ConcurrentHashMap<String, Entry>()

    fun started(id: String, name: String) {
        running[id] = Entry(name, nanoTime())
    }

    fun finished(id: String) {
        running.remove(id)
    }

    /** The tests over the limit, longest first. A test is reported once: it leaves the list. */
    fun takeOverdue(): List<Overdue> {
        val now = nanoTime()
        val overdue = running.entries
            .filter { Duration.ofNanos(now - it.value.startedAt) > limit }
            .onEach { running.remove(it.key) }
            .map { Overdue(it.value.name, Duration.ofNanos(now - it.value.startedAt)) }
        return overdue.sortedByDescending { it.running }
    }
}
