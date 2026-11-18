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
        return compileWith(corpusCase, work, ["-A${SCHEMA_DIRS}=${schemaDir}".toString()])
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
