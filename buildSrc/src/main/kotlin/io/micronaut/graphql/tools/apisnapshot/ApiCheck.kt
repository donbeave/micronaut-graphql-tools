package io.micronaut.graphql.tools.apisnapshot

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Compares the generated listing of a module with its reviewed snapshot and fails with the difference. */
abstract class ApiCheck : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val listing: RegularFileProperty

    /** A collection, not a file property: the snapshot may be missing, and that is a failure with its own message. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val snapshot: ConfigurableFileCollection

    @get:Input
    abstract val snapshotPath: Property<String>

    @get:Input
    abstract val dumpTask: Property<String>

    @TaskAction
    fun check() {
        val file = snapshot.singleFile
        val how = "If the change is intended, run `./gradlew ${dumpTask.get()}`, review the diff of ${snapshotPath.get()} and commit it " +
            "with the change. Otherwise undo the change to the signature."
        if (!file.isFile) {
            throw GradleException("The snapshot of the public API ${snapshotPath.get()} does not exist. $how")
        }
        val difference = difference(parse(file.readText()), parse(listing.get().asFile.readText()))
        if (difference.isNotEmpty()) {
            throw GradleException("The public API differs from the reviewed snapshot ${snapshotPath.get()} (- snapshot, + build):\n\n" +
                difference.joinToString("\n") + "\n\n$how")
        }
    }

    private class Type(val header: String, val members: List<String>)

    /** The listing as type name to its header and member lines (verbatim). */
    private fun parse(text: String): Map<String, Type> {
        val result = LinkedHashMap<String, Type>()
        var header = ""
        var members = ArrayList<String>()
        for (line in text.lines()) {
            when {
                line.isEmpty() -> Unit
                line == "}" -> result[TYPE_NAME.find(header)!!.groupValues[1]] = Type(header, members)
                line.startsWith("  ") -> members += line.trim()
                else -> {
                    header = line.removeSuffix(" {")
                    members = ArrayList()
                }
            }
        }
        return result
    }

    private fun difference(snapshot: Map<String, Type>, built: Map<String, Type>): List<String> = buildList {
        for (name in (snapshot.keys + built.keys).sorted()) {
            val before = snapshot[name]
            val after = built[name]
            when {
                after == null -> add("- ${before!!.header}")
                before == null -> add("+ ${after.header}")
                before.header != after.header || before.members != after.members -> {
                    if (before.header == after.header) {
                        add("  ${after.header}")
                    } else {
                        add("  - ${before.header}")
                        add("  + ${after.header}")
                    }
                    (before.members - after.members.toSet()).forEach { add("    - $it") }
                    (after.members - before.members.toSet()).forEach { add("    + $it") }
                }
            }
        }
    }

    private companion object {
        val TYPE_NAME = Regex("""\b(?:class|interface)\s+([^\s<]+)""")
    }
}
