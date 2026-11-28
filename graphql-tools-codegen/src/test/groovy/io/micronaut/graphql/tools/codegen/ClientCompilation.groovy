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
package io.micronaut.graphql.tools.codegen

import javax.annotation.processing.Processor
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaCompiler
import javax.tools.JavaFileObject
import javax.tools.StandardJavaFileManager
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Compiles generated client sources, and the Java that calls them, to a directory with javac. Two forms:
 * {@link #plain} compiles against the class path that the guide documents for the generated sources (the client module and the JSON
 * implementation of Micronaut Serialization, from the system property {@code codegen.client.classpath}) and runs no processor;
 * {@link #checked} compiles with the class path of the tests, the Micronaut and Serde processors and the visitor of the compile-time
 * checks, over the client directory that holds the schema and the operations, so that the result types of the declarative client
 * methods are checked against the selections.
 */
class ClientCompilation {

    private static final String CLIENT_DIRS = 'micronaut.graphql.tools.processor.client.dirs'

    static Result plain(Path work, List<GeneratedFile> generated, Map<String, String> extra = [:]) {
        run(work, generated, extra, null, false)
    }

    static Result checked(Path work, List<GeneratedFile> generated, Map<String, String> extra, Path clientSchemaDir) {
        run(work, generated, extra, clientSchemaDir, true)
    }

    private static Result run(Path work, List<GeneratedFile> generated, Map<String, String> extra, Path clientSchemaDir, boolean processors) {
        Path sources = Files.createDirectories(work.resolve('sources'))
        Path classes = Files.createDirectories(work.resolve('classes'))
        List<Path> units = []
        generated.findAll { it.path().endsWith('.java') }.each { units << write(sources, it.path(), it.content()) }
        extra.each { path, content -> units << write(sources, path, content) }
        JavaCompiler compiler = ToolProvider.systemJavaCompiler
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>()
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)
        String classPath = processors ? System.getProperty('java.class.path') : System.getProperty('codegen.client.classpath')
        assert classPath: 'the class path of the compilation is not set'
        fileManager.setLocation(StandardLocation.CLASS_PATH, classPath.split(File.pathSeparator).collect { new File(it) })
        fileManager.setLocation(StandardLocation.CLASS_OUTPUT, [classes.toFile()])
        List<String> arguments = ['--release', '17', '-parameters', '-Xlint:none']
        if (processors) {
            arguments.add('-proc:full')
            Path client = Files.createDirectories(work.resolve('client/models'))
            clientSchemaDir.toFile().eachFileRecurse { File file ->
                if (file.isFile() && (file.name.endsWith('.graphqls') || file.name.endsWith('.graphql'))) {
                    Path target = client.resolve(clientSchemaDir.relativize(file.toPath()).toString())
                    Files.createDirectories(target.parent)
                    Files.copy(file.toPath(), target)
                }
            }
            arguments.add("-A${CLIENT_DIRS}=${work.resolve('client')}".toString())
        } else {
            arguments.add('-proc:none')
        }
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, arguments, null,
                fileManager.getJavaFileObjectsFromPaths(units))
        if (processors) {
            task.setProcessors(ServiceLoader.load(Processor, ClientCompilation.classLoader).toList() as List<Processor>)
        }
        Properties saved = (Properties) System.properties.clone()
        boolean success
        try {
            System.properties.stringPropertyNames().findAll { it.startsWith('micronaut.graphql.tools.') }.each { System.clearProperty(it) }
            success = task.call()
        } finally {
            System.properties = saved
            fileManager.close()
        }
        new Result(success, diagnostics.diagnostics, classes)
    }

    private static Path write(Path root, String path, String content) {
        Path file = root.resolve(path)
        Files.createDirectories(file.parent)
        Files.writeString(file, content)
    }

    static class Result {

        final boolean success
        final List<Diagnostic<? extends JavaFileObject>> diagnostics
        final Path classes

        Result(boolean success, List<Diagnostic<? extends JavaFileObject>> diagnostics, Path classes) {
            this.success = success
            this.diagnostics = diagnostics
            this.classes = classes
        }

        List<String> getErrors() {
            diagnostics.findAll { it.kind == Diagnostic.Kind.ERROR }.collect { it.getMessage(Locale.ROOT) }
        }

        URLClassLoader loader() {
            new URLClassLoader([classes.toUri().toURL()] as URL[], ClientCompilation.classLoader)
        }

        /**
         * A loader of the classes over the runtime class path of the documented dependencies only, with no other parent: the class path
         * of an application that calls a server, without the library of the server.
         */
        URLClassLoader isolated() {
            List<URL> urls = [classes.toUri().toURL()]
            System.getProperty('codegen.client.runtime.classpath').split(File.pathSeparator).each { urls << new File(it).toURI().toURL() }
            new URLClassLoader(urls as URL[], ClassLoader.platformClassLoader)
        }

        @Override
        String toString() {
            (success ? 'success' : 'failure') + diagnostics.collect { '\n  ' + it.kind + ' ' + it.getMessage(Locale.ROOT) }.join('')
        }
    }
}
