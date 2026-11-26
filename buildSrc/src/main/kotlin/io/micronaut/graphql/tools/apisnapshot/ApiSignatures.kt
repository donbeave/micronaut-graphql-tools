package io.micronaut.graphql.tools.apisnapshot

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.spi.ToolProvider

/**
 * Writes the public member signatures of the main classes of one module: every public or protected member of every public
 * type that is outside an `internal` package, is not annotated `@Internal` (nor nested in such a type), and is not a generated
 * Micronaut `$...` class. The text comes from javap and is sorted, so it does not depend on the order of the class file, nor
 * on the JDK that runs the build: javap's own banner lines, descriptors and blank lines are dropped.
 */
@CacheableTask
abstract class ApiSignatures : DefaultTask() {
    @get:Classpath
    abstract val classes: ConfigurableFileCollection

    @get:OutputFile
    abstract val listing: RegularFileProperty

    @TaskAction
    fun write() {
        listing.get().asFile.apply { parentFile.mkdirs() }.writeText(ApiListing.generate(classes.files))
    }
}

/** The listing of the classes under the given directories; the task above only writes it, and the tests call it directly. */
internal object ApiListing {
    private val HEADER_MODIFIERS = setOf("public", "protected", "private", "static", "final", "abstract", "strictfp", "sealed")

    fun generate(classDirectories: Collection<File>): String {
        val all = classDirectories.filter { it.isDirectory }.flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "class" }.map(ClassFileInfo::read).toList()
        }.associateBy { it.name }
        val exposed = all.values.filter { isExposed(it, all) }.sortedBy { it.name }
        val text = StringBuilder()
        if (exposed.isNotEmpty()) {
            val lines = javap(exposed.map { it.file })
            exposed.forEach { info -> text.append(render(info, lines.getValue(info.file))).append('\n') }
        }
        return if (text.isEmpty()) "" else text.toString().trimEnd() + "\n"
    }

    private fun isExposed(info: ClassFileInfo, all: Map<String, ClassFileInfo>): Boolean {
        if (info.simpleFileName.startsWith("$") || "internal" in info.packageSegments || info.internal) return false
        val outer = info.outerName
        return if (outer == null) {
            info.access and 0x0001 != 0
        } else {
            info.innerAccess!! and 0x0005 != 0 && all[outer]?.let { isExposed(it, all) } == true
        }
    }

    private fun javap(files: List<File>): Map<File, List<String>> {
        val tool = ToolProvider.findFirst("javap").orElseThrow { GradleException("javap is not available: run Gradle on a JDK, not a JRE") }
        val result = LinkedHashMap<File, List<String>>()
        // one run per class keeps the pairing of a class file and its listing trivial; javap itself is in process and cheap
        for (file in files) {
            val out = StringWriter()
            val err = StringWriter()
            val status = tool.run(PrintWriter(out), PrintWriter(err), "-protected", "-s", "-constants", file.absolutePath)
            if (status != 0) throw GradleException("javap failed on $file: $err")
            result[file] = out.toString().lines()
        }
        return result
    }

    /** One class as text: the header, then the sorted member declarations, each with its javap descriptor line removed. */
    private fun render(info: ClassFileInfo, lines: List<String>): String {
        val header = lines.single { it.isNotEmpty() && !it[0].isWhitespace() && it.endsWith(" {") }
        val members = ArrayList<Pair<String, String>>()
        for (line in lines) {
            if (line.startsWith("    descriptor: ")) {
                members[members.lastIndex] = members.last().first to line.removePrefix("    descriptor: ")
            } else if (line.startsWith("  ") && !line.startsWith("   ")) {
                members += line.trim() to ""
            }
        }
        val exposed = info.members.filter { it.exposed }
        if (exposed.size != members.size || exposed.zip(members).any { (member, printed) -> member.descriptor != printed.second }) {
            throw GradleException("javap and the class file disagree on the members of ${info.name}")
        }
        val kept = exposed.zip(members).filterNot { (member, _) -> member.internal }
            .sortedWith(compareBy({ if (it.first.enumConstant) 0 else 1 }, { if (it.first.enumConstant) "" else it.second.first }))
            .map { it.second.first }
        return buildString {
            appendLine(permits(info, header(info, header)))
            kept.forEach { appendLine("  $it") }
            appendLine("}")
        }
    }

    private fun permits(info: ClassFileInfo, header: String): String =
        if (info.permitted.isEmpty()) {
            header
        } else {
            header.removeSuffix(" {") + " permits " + info.permitted.map { it.replace('/', '.') }.sorted().joinToString(", ") + " {"
        }

    private fun header(info: ClassFileInfo, javapHeader: String): String {
        val flags = info.innerAccess ?: return javapHeader
        val rest = javapHeader.split(' ').dropWhile { it in HEADER_MODIFIERS }.joinToString(" ")
        val modifiers = buildList {
            if (flags and 0x0001 != 0) add("public")
            if (flags and 0x0004 != 0) add("protected")
            if (flags and 0x0400 != 0 && flags and 0x0200 == 0) add("abstract")
            if (flags and 0x0008 != 0) add("static")
            if (flags and 0x0010 != 0) add("final")
        }
        return (modifiers + rest).joinToString(" ")
    }
}
