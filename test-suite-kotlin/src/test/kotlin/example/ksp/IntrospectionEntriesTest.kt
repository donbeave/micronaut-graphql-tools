/*
 * Copyright 2021-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package example.ksp

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

// Runs on the JVM only (the native binary has no build directory): every introspection class that KSP generated for the test
// source set has its BeanIntrospectionReference entry. An incremental KSP run used to drop the entries of the classes of another
// module while the classes stayed, so a bean introspection was missing until a clean build (#566).
class IntrospectionEntriesTest {

    @Test
    fun `every generated introspection class has a reference entry`() {
        val output = System.getProperty("ksp.test.output")?.let(::File)
        assumeTrue(output != null && output.isDirectory, "no KSP output directory: not a JVM run from Gradle")
        val classesDir = File(output, "classes")
        val classes = classesDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith("\$Introspection.class") }
            .map { it.relativeTo(classesDir).path.removeSuffix(".class").replace(File.separatorChar, '.') }
            .toSortedSet()
        val entries = File(output, "resources/META-INF/micronaut/io.micronaut.core.beans.BeanIntrospectionReference").list()
            .orEmpty().toSortedSet()
        assertEquals(classes, entries)
    }
}
