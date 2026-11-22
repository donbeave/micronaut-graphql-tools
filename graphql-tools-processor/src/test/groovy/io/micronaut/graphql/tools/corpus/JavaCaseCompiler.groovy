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
 * In-process javac (javax.tools) over the test classpath, with the annotation processors of the test classpath. The visitor of this module, which the test resources
 * register, is switched off here ({@link #CHECKS_OFF}): the classes are built to be started, and checked by the compile-time
 * binder. A case that says {@code compile.processor=absent} is compiled with Micronaut's processors of a {@link ProcessorlessLoader}, which has no visitor. Micronaut's processors copy the {@code -Amicronaut.*} options into the system properties, so every
 * {@code micronaut.graphql.tools.processor.*} property is cleared, and the properties are restored, around each compilation.
 */
@CompileStatic
final class JavaCaseCompiler implements CheckedCaseCompiler {

    /** The visitor of this module is on the test classpath: this compilation only builds the case, a binder checks it. */
    /** The runtime key of a case that the visitor reads as an option of its own. */
    static final String ACCESSOR_PREFIXES_CONFIG = 'micronaut.graphql.tools.resolver-accessor-prefixes'

    static final String ACCESSOR_PREFIXES = 'micronaut.graphql.tools.processor.resolver.accessor.prefixes'

    /** The runtime key of a case that the visitor reads as an option of its own. */
    static final String TYPE_CLASS_CONVENTION_CONFIG = 'micronaut.graphql.tools.type-class-convention'

    static final String TYPE_CLASS_CONVENTION = 'micronaut.graphql.tools.processor.type.class.convention'

    static final String CHECKS_OFF = 'micronaut.graphql.tools.processor.checks=false'

    @Override
    Language getLanguage() {
        return Language.JAVA
    }

    @Override
    CompiledCase compile(CorpusCase corpusCase, Path work) {
        List<Path> outputs = []
        for (SourceUnit unit : corpusCase.units(Language.JAVA)) {
            CompiledCase compiled = compileUnit(unit, work.resolve('unit-' + outputs.size()), outputs, ['-A' + CHECKS_OFF], corpusCase.processorAbsent())
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
        List<String> checked = CompileOptions.of(corpusCase, work).collect { String key, String value -> "-A${key}=${value}".toString() }
        String prefixes = corpusCase.config[ACCESSOR_PREFIXES_CONFIG]
        if (prefixes != null) {
            checked.add("-A${ACCESSOR_PREFIXES}=${prefixes}".toString())
        }
        String convention = corpusCase.config[TYPE_CLASS_CONVENTION_CONFIG]
        if (convention != null) {
            checked.add("-A${TYPE_CLASS_CONVENTION}=${convention}".toString())
        }
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

    private CompiledCase compileUnit(SourceUnit unit, Path work, List<Path> dependencies, List<String> options, boolean processorless = false) {
        Path classes = Files.createDirectories(work.resolve('classes'))
        JavaCompiler compiler = ToolProvider.systemJavaCompiler
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>()
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)
        Properties saved = (Properties) System.properties.clone()
        URLClassLoader processors = processorless ? ProcessorlessLoader.create() : null
        try {
            List<File> classpath = System.getProperty('java.class.path').split(File.pathSeparator).collect { new File(it) }
            classpath.addAll(dependencies.collect { it.toFile() })
            files.setLocation(StandardLocation.CLASS_PATH, classpath)
            files.setLocation(StandardLocation.CLASS_OUTPUT, [classes.toFile()])
            List<Processor> found = new ArrayList<>()
            ServiceLoader.load(Processor, processors ?: getClass().classLoader).each { found.add(it) }
            JavaCompiler.CompilationTask task = compiler.getTask(null, files, diagnostics,
                    ['-proc:full'] + options,
                    null, files.getJavaFileObjectsFromPaths(unit.sources))
            task.setProcessors(found)
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
            processors?.close()
            System.properties = saved
        }
    }

    private static void clearSystemProperties() {
        System.properties.stringPropertyNames().findAll { it.startsWith('micronaut.graphql.tools.processor.') }
                .each { System.clearProperty(it) }
    }

}
