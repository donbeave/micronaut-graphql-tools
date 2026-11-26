package io.micronaut.graphql.tools.apisnapshot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import javax.tools.ToolProvider

class ApiSnapshotTest {
    @TempDir
    lateinit var work: File

    private val stubs = mapOf(
        "io/micronaut/core/annotation/Internal.java" to """
            package io.micronaut.core.annotation;
            import java.lang.annotation.*;
            @Retention(RetentionPolicy.RUNTIME)
            public @interface Internal {}
        """,
        "demo/Shape.java" to """
            package demo;
            import java.util.List;
            public class Shape<T> {
                public static final int SIDES = 4;
                public Shape() {}
                public List<T> corners(int count) { return null; }
                protected String label() { return "shape"; }
                String packagePrivate() { return ""; }
                private void secret() {}
                @io.micronaut.core.annotation.Internal public void forTheLibraryOnly() {}
                public static class Builder { public Shape<String> build() { return null; } }
                static class Hidden { public void x() {} }
                public enum Kind { ROUND, SQUARE }
            }
        """,
        "demo/internal/Impl.java" to "package demo.internal; public class Impl { public void run() {} }",
        "demo/Marked.java" to """
            package demo;
            @io.micronaut.core.annotation.Internal
            public class Marked { public static class Nested {} public void run() {} }
        """,
        "demo/${'$'}Shape${'$'}Definition.java" to "package demo; public class ${'$'}Shape${'$'}Definition { public void generated() {} }",
    )

    private fun compile(sources: Map<String, String>, output: File) {
        val src = File(work, "src-${output.name}")
        sources.forEach { (path, text) -> File(src, path).apply { parentFile.mkdirs() }.writeText(text.trimIndent()) }
        val compiler = ToolProvider.getSystemJavaCompiler()
        val files = src.walkTopDown().filter { it.extension == "java" }.map { it.path }.toList()
        output.mkdirs()
        check(compiler.run(null, null, null, "--release", "17", "-d", output.path, *files.toTypedArray()) == 0)
    }

    private fun listing(sources: Map<String, String>, name: String): String {
        val compiled = File(work, "classes-$name")
        compile(sources, compiled)
        return ApiListing.generate(listOf(compiled))
    }

    private fun failure(built: String, reviewed: String?): String? =
        ApiComparison.failure(reviewed, built, "demo/api/demo.api", ":demo:apiDump")

    @Test
    fun `the listing holds the public and protected members of the public types, sorted`() {
        val text = listing(stubs, "base")
        assertEquals(
            """
            public class demo.Shape<T> {
              protected java.lang.String label();
              public demo.Shape();
              public java.util.List<T> corners(int);
              public static final int SIDES = 4;
            }

            public static class demo.Shape${'$'}Builder {
              public demo.Shape${'$'}Builder();
              public demo.Shape<java.lang.String> build();
            }

            public static final class demo.Shape${'$'}Kind extends java.lang.Enum<demo.Shape${'$'}Kind> {
              public static final demo.Shape${'$'}Kind ROUND;
              public static final demo.Shape${'$'}Kind SQUARE;
              public static demo.Shape${'$'}Kind valueOf(java.lang.String);
              public static demo.Shape${'$'}Kind[] values();
            }

            public interface io.micronaut.core.annotation.Internal extends java.lang.annotation.Annotation {
            }
            """.trimIndent() + "\n",
            text,
        )
        assertFalse(text.contains("Impl"), "types of an internal package are left out")
        assertFalse(text.contains("Marked"), "a type annotated @Internal and its nested types are left out")
        assertFalse(text.contains("forTheLibraryOnly"), "a member annotated @Internal is left out")
        assertFalse(text.contains("Hidden") || text.contains("secret") || text.contains("packagePrivate"))
        assertFalse(text.contains("Definition"), "generated Micronaut classes are left out")
    }

    @Test
    fun `the permitted subclasses of a sealed type are part of the listing`() {
        val open = mapOf("demo/Node.java" to "package demo; public interface Node {}", "demo/Leaf.java" to "package demo; public final class Leaf implements Node {}")
        val sealed = mapOf(
            "demo/Node.java" to "package demo; public sealed interface Node permits Leaf {}",
            "demo/Leaf.java" to "package demo; public final class Leaf implements Node {}",
        )
        val message = failure(listing(sealed, "sealed"), listing(open, "open"))!!
        assertTrue(message.contains("+ public interface demo.Node permits demo.Leaf"), message)
    }

    @Test
    fun `a build whose signatures did not change passes`() {
        val snapshot = listing(stubs, "before")
        val changedBody = stubs + ("demo/Shape.java" to stubs.getValue("demo/Shape.java").replace("return \"shape\";", "return \"other\";"))
        assertNull(failure(listing(changedBody, "after"), snapshot))
    }

    @Test
    fun `a changed public signature fails and names the difference and the way to update the snapshot`() {
        val snapshot = listing(stubs, "before")
        val changed = stubs + ("demo/Shape.java" to stubs.getValue("demo/Shape.java").replace("corners(int count)", "corners(long count)"))
        val message = failure(listing(changed, "after"), snapshot)!!
        assertTrue(message.contains("- public java.util.List<T> corners(int);"), message)
        assertTrue(message.contains("+ public java.util.List<T> corners(long);"), message)
        assertTrue(message.contains("./gradlew :demo:apiDump"), message)
    }

    @Test
    fun `a new public type and a removed one fail`() {
        val snapshot = listing(stubs, "before")
        val changed = stubs + ("demo/Extra.java" to "package demo; public interface Extra {}") - "demo/Shape.java"
        val message = failure(listing(changed, "after"), snapshot)!!
        assertTrue(message.contains("+ public interface demo.Extra"), message)
        assertTrue(message.contains("- public class demo.Shape<T>"), message)
    }

    @Test
    fun `a missing snapshot fails with the way to create it`() {
        val message = failure(listing(stubs, "before"), null)!!
        assertTrue(message.contains("does not exist") && message.contains("apiDump"), message)
    }
}
