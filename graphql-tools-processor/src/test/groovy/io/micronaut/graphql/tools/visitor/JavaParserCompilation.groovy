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
package io.micronaut.graphql.tools.visitor

import groovy.transform.CompileStatic
import io.micronaut.annotation.processing.test.JavaParser

import javax.tools.JavaFileObject
import java.nio.file.Files
import java.nio.file.Path

/**
 * Compiles Java sources in memory with Micronaut's {@link JavaParser}, which runs the visitor that the test resources
 * register. JavaParser takes no processor options, so they are set as system properties, which the visitor reads as it
 * reads {@code -A} options; the properties are cleared before the compilation and restored after it. The class files that
 * the compilation wrote are copied to a directory, for a test that starts an application from them. JavaParser also takes no
 * class path, so a directory to compile against is appended to {@code java.class.path}, which its compiler reads.
 */
@CompileStatic
class JavaParserCompilation {

    private static final String CLASS_OUTPUT = '/CLASS_OUTPUT/'

    private final Map<String, String> sources = new LinkedHashMap<>()
    private final Map<String, String> schemaFiles = new LinkedHashMap<>()
    private final Map<String, String> clientFiles = new LinkedHashMap<>()
    private final Map<String, String> options = new LinkedHashMap<>()
    private final List<File> extraClasspath = []

    JavaParserCompilation schema(String path, String content) {
        schemaFiles.put(path, content)
        return this
    }

    /** A file of the client directory, by its path under it: {@code books/schema.graphqls} is the schema {@code books}. */
    JavaParserCompilation clientFile(String path, String content) {
        clientFiles.put(path, content)
        return this
    }

    JavaParserCompilation source(String className, String content) {
        sources.put(className, content)
        return this
    }

    JavaParserCompilation option(String name, String value) {
        options.put(name, value)
        return this
    }

    /** A directory of class files that the sources compile against. */
    JavaParserCompilation classpath(File directory) {
        extraClasspath.add(directory)
        return this
    }

    VisitorCompilation.Result compile() {
        Path work = Files.createTempDirectory('graphql-visitor-parse')
        Path schemaDir = work.resolve('graphql')
        Files.createDirectories(schemaDir)
        schemaFiles.each { path, content ->
            Path file = schemaDir.resolve(path)
            Files.createDirectories(file.parent)
            Files.writeString(file, content)
        }
        Path classes = Files.createDirectories(work.resolve('classes'))
        Path clientDir = work.resolve('client')
        clientFiles.each { path, content ->
            Path file = clientDir.resolve(path)
            Files.createDirectories(file.parent)
            Files.writeString(file, content)
        }

        Map<String, String> properties = new LinkedHashMap<>(options)
        properties.put(ProcessorOptions.SCHEMA_DIRS, schemaDir.toString())
        if (!clientFiles.isEmpty()) {
            properties.put(ProcessorOptions.CLIENT_DIRS, clientDir.toString())
        }
        Properties saved = (Properties) System.properties.clone()
        JavaParser parser = null
        boolean success = true
        try {
            System.properties.stringPropertyNames().findAll { it.startsWith('micronaut.graphql.tools.') }
                    .each { System.clearProperty(it) }
            properties.each { name, value -> System.setProperty(name, value) }
            if (!extraClasspath.isEmpty()) {
                System.setProperty('java.class.path', (saved.getProperty('java.class.path') + File.pathSeparator + extraClasspath.join(File.pathSeparator)))
            }
            parser = new JavaParser()
            try {
                JavaFileObject[] units = sources.collect { name, content ->
                    (JavaFileObject) new VisitorCompilation.Source(name, content)
                } as JavaFileObject[]
                for (JavaFileObject output : (Iterable<JavaFileObject>) parser.generate(units)) {
                    String path = output.toUri().path
                    if (path.startsWith(CLASS_OUTPUT)) {
                        Path target = classes.resolve(path.substring(CLASS_OUTPUT.length()))
                        Files.createDirectories(target.parent)
                        InputStream input = output.openInputStream()
                        try {
                            Files.copy(input, target)
                        } finally {
                            input.close()
                        }
                    }
                }
            } catch (RuntimeException ignored) {
                success = false
            }
            return new VisitorCompilation.Result(success, parser.diagnosticCollector.diagnostics, schemaDir.toFile(), classes.toFile())
        } finally {
            System.properties = saved
            parser?.close()
        }
    }

}
