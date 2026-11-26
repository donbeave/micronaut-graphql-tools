import io.micronaut.graphql.tools.testtimeout.TASK_PROPERTY
import io.micronaut.graphql.tools.testtimeout.TestTimeoutListener
import java.time.Duration

// A test that hangs fails its task fast, with the name of the test in the log. The slowest test of the default leg takes 36 s on CI
// (the build log of a green run), so the limit is 5 minutes: eight times the slowest test, and a small part of the 45 minutes of a job.
// -PtestTimeoutSeconds=<n> changes it for one run. The task limit is the backstop for a hang outside any test (a stuck fork).
// The native tests are not covered: they run on their own legs.
val testLimit = Duration.ofSeconds((findProperty("testTimeoutSeconds") as String? ?: "300").toLong())

tasks.withType<Test>().configureEach {
    if (name.startsWith("native")) {
        return@configureEach
    }
    timeout = Duration.ofMinutes(40)
    systemProperty("junit.jupiter.execution.timeout.default", "${testLimit.seconds}s")
    systemProperty(TASK_PROPERTY, path)
    addTestListener(TestTimeoutListener(path, testLimit, logger))
}
