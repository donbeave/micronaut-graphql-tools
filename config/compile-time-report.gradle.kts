import java.util.concurrent.atomic.AtomicLong

// The compile time of an example suite with the compile-time checks on (#52). The checks run inside the compile task of the language
// (javac, KSP, the Groovy compiler), so the time of that task is the cost the suite pays for them. Each task that runs appends one
// line, `<task> <milliseconds>`, to `build/reports/compile-times.txt`; the CI job `build` prints the files into its summary. The file is
// not cleared (the build of the drift fixture runs in the same directory), so a CI run, which starts clean, holds its own build only. A task
// that is up to date, or comes from the build cache, runs nothing and records nothing.
val compileTimeTasks = setOf("compileJava", "compileKotlin", "kspKotlin", "compileGroovy")
val compileTimeReport = layout.buildDirectory.file("reports/compile-times.txt")
tasks.matching { it.name in compileTimeTasks }.configureEach {
    val started = AtomicLong()
    val taskName = name
    val report = compileTimeReport
    doFirst { started.set(System.nanoTime()) }
    doLast {
        val millis = (System.nanoTime() - started.get()) / 1_000_000
        report.get().asFile.apply { parentFile.mkdirs() }.appendText("$taskName $millis\n")
    }
}
