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
package io.micronaut.graphql.tools.corpus

import groovy.transform.CompileStatic

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
 * In-process javac (javax.tools) over the test classpath, with the annotation processors of the test classpath except
 * the JSR-269 processor of this module: the runner never runs it. The visitor of this module, which the test resources
 * register, is switched off here ({@link #CHECKS_OFF}): the classes are built to be started, and checked by the compile-time
 * binder. Micronaut's processors copy the {@code -Amicronaut.*} options into the system properties, so every
 * {@code micronaut.graphql.tools.processor.*} property is cleared, and the properties are restored, around each compilation.
 */
@CompileStatic
final class JavaCaseCompiler implements CheckedCaseCompiler {

    static final String LEGACY_PROCESSOR_PACKAGE = 'io.micronaut.graphql.tools.processing'

    /** The visitor of this module is on the test classpath: this compilation only builds the case, a binder checks it. */
    static final String SCHEMA_DIRS = 'micronaut.graphql.tools.processor.schema.dirs'

    /** The runtime key of a case that the visitor reads as an option of its own. */
    static final String ACCESSOR_PREFIXES_CONFIG = 'micronaut.graphql.tools.resolver-accessor-prefixes'

    static final String ACCESSOR_PREFIXES = 'micronaut.graphql.tools.processor.resolverAccessorPrefixes'

    static final String CHECKS_OFF = 'micronaut.graphql.tools.processor.checks=false'

    @Override
    Language getLanguage() {
        return Language.JAVA
    }

    @Override
    CompiledCase compile(CorpusCase corpusCase, Path work) {
        return compileWith(corpusCase, work, ['-A' + CHECKS_OFF])
    }

    /**
     * Compiles the case with the compile-time checks on, the schema files of the case directory being the schema directory
     * of the checks. The result is not started: its errors and warnings are the findings of the visitor.
     */
    @Override
    CompiledCase compileChecked(CorpusCase corpusCase, Path work) {
        Path schemaDir = Files.createDirectories(work.resolve('schema'))
        corpusCase.schemaFiles().each { Files.copy(it, schemaDir.resolve(it.fileName.toString())) }
        List<String> options = ["-A${SCHEMA_DIRS}=${schemaDir}".toString()]
        String prefixes = corpusCase.config[ACCESSOR_PREFIXES_CONFIG]
        if (prefixes != null) {
            options.add("-A${ACCESSOR_PREFIXES}=${prefixes}".toString())
        }
        return compileWith(corpusCase, work, options)
    }

    private CompiledCase compileWith(CorpusCase corpusCase, Path work, List<String> options) {
        Path classes = Files.createDirectories(work.resolve('classes'))
        JavaCompiler compiler = ToolProvider.systemJavaCompiler
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>()
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)
        Properties saved = (Properties) System.properties.clone()
        try {
            List<File> classpath = System.getProperty('java.class.path').split(File.pathSeparator).collect { new File(it) }
            files.setLocation(StandardLocation.CLASS_PATH, classpath)
            files.setLocation(StandardLocation.CLASS_OUTPUT, [classes.toFile()])
            List<Processor> processors = new ArrayList<>()
            ServiceLoader.load(Processor, getClass().classLoader).each {
                if (!it.getClass().name.startsWith(LEGACY_PROCESSOR_PACKAGE)) {
                    processors.add(it)
                }
            }
            JavaCompiler.CompilationTask task = compiler.getTask(null, files, diagnostics,
                    ['-proc:full'] + options,
                    null, files.getJavaFileObjectsFromPaths(corpusCase.sources(Language.JAVA)))
            task.setProcessors(processors)
            clearSystemProperties()
            boolean success = task.call()
            List<String> errors = diagnostics.diagnostics.findAll { it.kind == Diagnostic.Kind.ERROR }
                    .collect { it.toString() }
            List<String> warnings = diagnostics.diagnostics
                    .findAll { it.kind == Diagnostic.Kind.WARNING || it.kind == Diagnostic.Kind.MANDATORY_WARNING }
                    .collect { it.toString() }
            return new CompiledCase(success, classes, errors, [], warnings)
        } finally {
            files.close()
            System.properties = saved
        }
    }

    private static void clearSystemProperties() {
        System.properties.stringPropertyNames().findAll { it.startsWith('micronaut.graphql.tools.processor.') }
                .each { System.clearProperty(it) }
    }

}
