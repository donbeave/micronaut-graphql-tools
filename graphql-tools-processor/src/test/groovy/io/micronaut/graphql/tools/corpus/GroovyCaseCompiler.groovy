package io.micronaut.graphql.tools.corpus

import groovy.transform.CompileStatic
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.Phases

import java.nio.file.Files
import java.nio.file.Path

/**
 * In-process Groovy compilation over the test classpath. Micronaut's global AST transformation (micronaut-inject-groovy)
 * writes the bean definitions next to the classes, as it does in a build. The compilation runs in the calling thread;
 * options of this library would travel as system properties here, the real Groovy carrier, so the
 * {@code micronaut.graphql.tools.processor.*} properties are cleared and restored around each compilation.
 */
@CompileStatic
final class GroovyCaseCompiler implements CaseCompiler {

    @Override
    Language getLanguage() {
        return Language.GROOVY
    }

    @Override
    CompiledCase compile(CorpusCase corpusCase, Path work) {
        Path classes = Files.createDirectories(work.resolve('classes'))
        Properties saved = (Properties) System.properties.clone()
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        try {
            System.properties.stringPropertyNames().findAll { it.startsWith('micronaut.graphql.tools.processor.') }
                    .each { System.clearProperty(it) }
            // the visitor of this module, which the test resources register, only builds the case here
            System.setProperty('micronaut.graphql.tools.processor.checks', 'false')
            CompilerConfiguration configuration = new CompilerConfiguration()
            configuration.targetDirectory = classes.toFile()
            configuration.parameters = true
            CompilationUnit unit = new CompilationUnit(configuration, null, loader)
            corpusCase.sources(Language.GROOVY).each { unit.addSource(it.toFile()) }
            try {
                unit.compile(Phases.OUTPUT)
            } catch (CompilationFailedException e) {
                return new CompiledCase(false, classes, [e.message])
            }
            return new CompiledCase(true, classes, [])
        } finally {
            loader.close()
            System.properties = saved
        }
    }

}
