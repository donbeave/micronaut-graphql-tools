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

    static final String CHECKS_OFF = 'micronaut.graphql.tools.processor.checks=false'

    @Override
    Language getLanguage() {
        return Language.JAVA
    }

    @Override
    CompiledCase compile(CorpusCase corpusCase, Path work) {
        List<Path> outputs = []
        for (SourceUnit unit : corpusCase.units(Language.JAVA)) {
            CompiledCase compiled = compileUnit(unit, work.resolve('unit-' + outputs.size()), outputs, ['-A' + CHECKS_OFF])
            if (!compiled.success) {
                return new CompiledCase(false, outputs.empty ? compiled.classes : outputs.first(), compiled.errors, outputs.drop(1))
            }
            outputs.add(compiled.classes)
        }
        return new CompiledCase(true, outputs.first(), [], outputs.drop(1))
    }

    /**
     * Compiles the case with the compile-time checks on, the schema files of the case directory being the schema directory
     * of the checks. The result is not started: its errors and warnings are the findings of the visitor. A case that splits its
     * sources into modules is compiled module by module, as a build does, each module seeing the unchecked output of the modules
     * before it, so that a module that fails its checks does not hide the findings of the next one.
     */
    @Override
    CompiledCase compileChecked(CorpusCase corpusCase, Path work) {
        Path schemaDir = Files.createDirectories(work.resolve('schema'))
        corpusCase.schemaFiles().each { Files.copy(it, schemaDir.resolve(it.fileName.toString())) }
        List<String> checked = ["-A${SCHEMA_DIRS}=${schemaDir}".toString()]
        List<SourceUnit> units = corpusCase.units(Language.JAVA)
        List<Path> dependencies = []
        List<String> errors = []
        List<String> warnings = []
        Path classes = null
        boolean success = true
        units.eachWithIndex { SourceUnit unit, int index ->
            CompiledCase compiled = compileUnit(unit, work.resolve('checked-' + index), dependencies, checked)
            classes = classes ?: compiled.classes
            success = success && compiled.success
            errors.addAll(compiled.errors)
            warnings.addAll(compiled.warnings)
            if (index < units.size() - 1) {
                CompiledCase unchecked = compileUnit(unit, work.resolve('unchecked-' + index), dependencies, ['-A' + CHECKS_OFF])
                dependencies.add(unchecked.classes)
            }
        }
        return new CompiledCase(success, classes, errors, [], warnings)
    }

    private CompiledCase compileUnit(SourceUnit unit, Path work, List<Path> dependencies, List<String> options) {
        Path classes = Files.createDirectories(work.resolve('classes'))
        JavaCompiler compiler = ToolProvider.systemJavaCompiler
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>()
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)
        Properties saved = (Properties) System.properties.clone()
        try {
            List<File> classpath = System.getProperty('java.class.path').split(File.pathSeparator).collect { new File(it) }
            classpath.addAll(dependencies.collect { it.toFile() })
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
                    null, files.getJavaFileObjectsFromPaths(unit.sources))
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
