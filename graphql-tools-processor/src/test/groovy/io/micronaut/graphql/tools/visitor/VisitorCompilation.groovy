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

import javax.annotation.processing.Processor
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaCompiler
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import javax.tools.StandardJavaFileManager
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Compiles Java sources in process with javac and the Micronaut processors of the test classpath, with the visitor that the
 * test resources register. The JSR-269 processor of this module is left out, and the options that Micronaut copies into the
 * system properties are cleared before the compilation and restored after it.
 */
@CompileStatic
class VisitorCompilation {

    private final Map<String, String> sources = new LinkedHashMap<>()
    private final Map<String, String> schemaFiles = new LinkedHashMap<>()
    private final Map<String, String> options = new LinkedHashMap<>()
    private boolean schemaOption = true
    private boolean ownModuleDir
    private final List<File> extraDirectories = []
    private final List<File> extraClasspath = []
    private boolean parametersFlag = true

    VisitorCompilation schema(String path, String content) {
        schemaFiles.put(path, content)
        return this
    }

    VisitorCompilation source(String className, String content) {
        sources.put(className, content)
        return this
    }

    VisitorCompilation option(String name, String value) {
        options.put(name, value)
        return this
    }

    /** Directories that follow the one with the schema files of this compilation in the option. */
    VisitorCompilation extraSchemaDirectory(File directory) {
        extraDirectories.add(directory)
        return this
    }

    /** A directory of class files that the sources compile against and that the runtime loads with them. */
    VisitorCompilation classpath(File directory) {
        extraClasspath.add(directory)
        return this
    }

    /** Compiles without {@code -parameters}, as a build that does not set it would. */
    VisitorCompilation withoutParametersFlag() {
        parametersFlag = false
        return this
    }

    /** Passes the directory of this compilation as {@code module.dir}, as the documented build snippets do. */
    VisitorCompilation ownModuleDirOption() {
        ownModuleDir = true
        return this
    }

    VisitorCompilation withoutSchemaOption() {
        schemaOption = false
        return this
    }

    Result compile() {
        Path work = Files.createTempDirectory('graphql-visitor-test')
        Path schemaDir = work.resolve('graphql')
        Files.createDirectories(schemaDir)
        schemaFiles.each { path, content ->
            Path file = schemaDir.resolve(path)
            Files.createDirectories(file.parent)
            Files.writeString(file, content)
        }
        Path classes = Files.createDirectories(work.resolve('classes'))

        JavaCompiler compiler = ToolProvider.systemJavaCompiler
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>()
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)
        fileManager.setLocation(StandardLocation.CLASS_PATH,
                System.getProperty('java.class.path').split(File.pathSeparator).collect { new File(it) } + extraClasspath)
        fileManager.setLocation(StandardLocation.CLASS_OUTPUT, [classes.toFile()])

        List<String> arguments = ['-proc:full']
        if (parametersFlag) {
            arguments.add('-parameters')
        }
        if (schemaOption) {
            arguments.add("-A${ProcessorOptions.SCHEMA_DIRS}=${([schemaDir.toFile()] + extraDirectories).join(File.pathSeparator)}".toString())
        }
        if (ownModuleDir) {
            arguments.add("-A${ProcessorOptions.MODULE_DIR}=${work}".toString())
        }
        options.each { name, value -> arguments.add("-A${name}=${value}".toString()) }
        List<JavaFileObject> units = sources.collect { name, content -> (JavaFileObject) new Source(name, content) }
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, arguments, null, units)
        task.setProcessors(ServiceLoader.load(Processor, getClass().classLoader).findAll {
            !it.getClass().name.startsWith('io.micronaut.graphql.tools.processing')
        } as List<Processor>)
        Properties saved = (Properties) System.properties.clone()
        boolean success
        try {
            System.properties.stringPropertyNames().findAll { it.startsWith('micronaut.graphql.tools.') }
                    .each { System.clearProperty(it) }
            success = task.call()
        } finally {
            System.properties = saved
            fileManager.close()
        }
        return new Result(success, diagnostics.diagnostics, schemaDir.toFile(), classes.toFile())
    }

    @CompileStatic
    static class Result {

        final boolean success
        final List<Diagnostic<? extends JavaFileObject>> diagnostics
        final File schemaDir
        final File classOutput

        Result(boolean success, List<Diagnostic<? extends JavaFileObject>> diagnostics, File schemaDir, File classOutput) {
            this.success = success
            this.diagnostics = diagnostics
            this.schemaDir = schemaDir
            this.classOutput = classOutput
        }

        List<String> getErrors() {
            return diagnostics.findAll { it.kind == Diagnostic.Kind.ERROR }.collect { it.getMessage(Locale.ROOT) }
        }

        /** the first line of each error that carries a code: {@code [code] coordinate} */
        List<String> getFindings() {
            return errors.findAll { it.startsWith('[') }.collect { it.readLines().first().replaceFirst(/: .*/, '') }
        }

        List<String> getNotes() {
            return diagnostics.findAll { it.kind == Diagnostic.Kind.NOTE && it.getMessage(Locale.ROOT).startsWith('[graphql-tools]') }
                    .collect { it.getMessage(Locale.ROOT) }
        }

        Diagnostic<? extends JavaFileObject> errorFor(String codeAndCoordinate) {
            return diagnostics.find { it.kind == Diagnostic.Kind.ERROR && it.getMessage(Locale.ROOT).startsWith(codeAndCoordinate) }
        }

        @Override
        String toString() {
            return (success ? 'success' : 'failure') + diagnostics.collect { '\n  ' + it.kind + ' ' + it.getMessage(Locale.ROOT) }.join('')
        }
    }

    @CompileStatic
    private static class Source extends SimpleJavaFileObject {

        private final String content

        Source(String className, String content) {
            super(URI.create('string:///' + className.replace('.', '/') + JavaFileObject.Kind.SOURCE.extension),
                    JavaFileObject.Kind.SOURCE)
            this.content = content
        }

        @Override
        CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return content
        }
    }

}
