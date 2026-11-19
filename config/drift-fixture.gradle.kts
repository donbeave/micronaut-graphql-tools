import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

// The drift test of an example suite (#291). The suite has a `src/driftFixture` source set with a class that drifts from the
// schema on purpose, compiled with the same processor and options as `main`. The task `driftFixtureTest` compiles it in a build of
// its own (a failing compile task fails the build that runs it, so it cannot be asserted from inside the same build) and passes
// only when that build fails in the expected task with the expected `[code] coordinate` line.
//
// The suite applies this script after it has created the source set and sets, on the project (`extra`):
//   driftCompileTask    the task of the build that compiles the fixture, for example `compileDriftFixtureJava`
//   driftFailingTask    the task the failure is reported for (KSP reports it for its own task)
//   driftInputs         the sources and the classpaths of the fixture, which the test task declares as inputs

abstract class DriftFixtureTest : DefaultTask() {

    @get:Input
    abstract val compileTask: Property<String>

    @get:Input
    abstract val failingTask: Property<String>

    @get:Input
    abstract val expectedLine: Property<String>

    @get:Input
    abstract val forwardedProperties: ListProperty<String>

    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val fixtureInputs: ConfigurableFileCollection

    @get:OutputFile
    abstract val marker: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun run() {
        val output = ByteArrayOutputStream()
        val root = rootDirectory.get().asFile
        val result = execOperations.exec {
            workingDir = root
            commandLine(listOf(root.resolve("gradlew").absolutePath, compileTask.get(), "--no-build-cache", "--no-configuration-cache",
                "--console=plain", "--no-daemon") + forwardedProperties.get())
            isIgnoreExitValue = true
            standardOutput = output
            errorOutput = output
        }
        val log = output.toString(Charsets.UTF_8)
        if (result.exitValue == 0) {
            throw GradleException("The drift fixture compiled, but it must fail: ${compileTask.get()} did not fail.\n$log")
        }
        if (!log.contains("Execution failed for task '${failingTask.get()}'")) {
            throw GradleException("The build failed, but not in ${failingTask.get()}.\n$log")
        }
        if (!log.contains(expectedLine.get())) {
            throw GradleException("The failure of ${failingTask.get()} does not have the line '${expectedLine.get()}'.\n$log")
        }
        marker.get().asFile.apply { parentFile.mkdirs() }.writeText("${expectedLine.get()}\n")
    }
}

val driftCompileTask = project.extra["driftCompileTask"] as String
val driftFailingTask = project.extra["driftFailingTask"] as String

val driftFixtureTest = tasks.register<DriftFixtureTest>("driftFixtureTest") {
    description = "Asserts that the drift fixture fails the compilation with the expected [code] coordinate line."
    group = "verification"
    compileTask.set("${project.path}:$driftCompileTask")
    failingTask.set("${project.path}:$driftFailingTask")
    expectedLine.set("[argument-mismatch] Query.searchProfile")
    forwardedProperties.set(gradle.startParameter.projectProperties.map { (name, value) -> "-P$name=$value" })
    rootDirectory.set(rootProject.layout.projectDirectory)
    fixtureInputs.from(project.extra["driftInputs"])
    marker.set(layout.buildDirectory.file("checks/drift-fixture.ok"))
    // the build of the fixture finds the classes of `main` up to date; it must not build them in parallel with this build
    dependsOn(tasks.named("classes"))
}
tasks.named("check") { dependsOn(driftFixtureTest) }
