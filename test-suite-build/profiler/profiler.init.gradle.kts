// Init script of the compile-time profile (#418). gradle-profiler runs it with `-I` in each profiled sample.
//   - editSchema appends a new comment line to the schema file of the project, so that a scenario can edit the schema inside
//     the measured build; the compile tasks run after it
//   - editBody does the same to the resolver source file BookQuery, a change that leaves its signatures alone
//   - every compile task that can run the checks (javac, kapt, KSP, the Groovy compiler) appends `<scenario> <project> <task>
//     <milliseconds>` to the file in COMPILE_TIME_LOG, which is how the cost of the checks is reported per module
val compileTasks = setOf("compileJava", "compileKotlin", "kspKotlin", "kaptKotlin", "compileGroovy")
val scenario = gradle.startParameter.projectProperties["profilerScenario"] ?: "unnamed"
val logFile = System.getenv("COMPILE_TIME_LOG")

allprojects {
    val project = this
    val schemaFile = file("src/main/resources/graphql/schema.graphqls")
    if (schemaFile.exists()) {
        tasks.register("editSchema") {
            doLast {
                schemaFile.writeText(schemaFile.readText().replace(Regex("(?m)^# profiler edit .*\n"), "") + "# profiler edit ${System.nanoTime()}\n")
            }
        }
    }
    val bodyFile = fileTree("src/main") { include("**/BookQuery.*") }.files.find { it.name.startsWith("BookQuery.") }
    if (bodyFile != null) {
        tasks.register("editBody") {
            doLast {
                bodyFile.writeText(bodyFile.readText().replace(Regex("(?m)^// profiler edit .*\n"), "") + "// profiler edit ${System.nanoTime()}\n")
            }
        }
    }
    tasks.matching { it.name in compileTasks }.configureEach {
        val task = this
        val started = LongArray(1)
        val projectPath = project.path
        mustRunAfter(java.util.concurrent.Callable { project.rootProject.allprojects.flatMap { listOf(it.tasks.findByName("editSchema"), it.tasks.findByName("editBody")) }.filterNotNull() })
        doFirst { started[0] = System.nanoTime() }
        doLast {
            if (logFile != null) {
                File(logFile).appendText("$scenario $projectPath ${task.name} ${(System.nanoTime() - started[0]) / 1_000_000}\n")
            }
        }
    }
}
