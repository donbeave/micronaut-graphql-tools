package io.micronaut.graphql.tools.testtimeout

import org.gradle.api.logging.Logger
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** The system property that names the test task in the command line of its test JVM, so that the JVM can be found. */
const val TASK_PROPERTY = "graphql.tools.test.task"

/**
 * Fails a test task fast when one test runs longer than [limit]: it logs the name of the test, then stops the test JVM of the
 * task, so Gradle fails the task at once instead of waiting for the job limit. Gradle has no timeout per test, and the JUnit
 * Platform has none for the Spock engine.
 */
class TestTimeoutListener(
    private val taskPath: String,
    private val limit: Duration,
    private val logger: Logger,
    private val stop: (String) -> Int = ::stopTestJvm,
    private val pollSeconds: Long = 5,
) : TestListener {
    private val tests = RunningTests(limit)
    private var scheduler: ScheduledExecutorService? = null

    @Synchronized
    override fun beforeSuite(suite: TestDescriptor) {
        if (scheduler == null) {
            scheduler = Executors.newSingleThreadScheduledExecutor { runnable -> Thread(runnable, "test-timeout-$taskPath").apply { isDaemon = true } }
                .also { it.scheduleWithFixedDelay(::check, pollSeconds, pollSeconds, TimeUnit.SECONDS) }
        }
    }

    @Synchronized
    override fun afterSuite(suite: TestDescriptor, result: TestResult) {
        if (suite.parent == null) {
            scheduler?.shutdownNow()
            scheduler = null
        }
    }

    override fun beforeTest(testDescriptor: TestDescriptor) {
        tests.started(idOf(testDescriptor), nameOf(testDescriptor))
    }

    override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
        tests.finished(idOf(testDescriptor))
    }

    internal fun check() {
        val overdue = tests.takeOverdue()
        if (overdue.isEmpty()) {
            return
        }
        overdue.forEach {
            logger.error("TEST TIMEOUT: $taskPath: '${it.name}' has run for ${it.running.seconds}s, over the limit of ${limit.seconds}s.")
        }
        val stopped = stop(taskPath)
        logger.error("TEST TIMEOUT: $taskPath: stopped $stopped test JVM(s); the task fails. Fix the test, or raise the limit with -PtestTimeoutSeconds.")
    }

    private fun idOf(test: TestDescriptor) = System.identityHashCode(test).toString() + ":" + nameOf(test)

    private fun nameOf(test: TestDescriptor) = "${test.className} > ${test.name}"
}

internal fun stopTestJvm(taskPath: String): Int {
    val marker = Regex("-D" + Regex.escape(TASK_PROPERTY) + "=" + Regex.escape(taskPath) + "(\\s|$)")
    val victims = ProcessHandle.current().descendants()
        .filter { process -> process.info().commandLine().map { marker.containsMatchIn(it) }.orElse(false) }
        .toList()
    victims.forEach { it.destroyForcibly() }
    return victims.size
}
