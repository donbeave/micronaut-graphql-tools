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
import io.micronaut.ast.groovy.TypeElementVisitorTransform
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.control.messages.Message
import org.codehaus.groovy.control.messages.SimpleMessage
import org.codehaus.groovy.control.messages.SyntaxErrorMessage

import java.lang.reflect.Field
import java.nio.file.Files
import java.nio.file.Path

/**
 * In-process Groovy compilation over the test classpath. Micronaut's global AST transformation (micronaut-inject-groovy)
 * writes the bean definitions next to the classes, and runs the visitor of this module, as it does in a build. The compilation
 * runs in the calling thread; the options of this library travel as system properties here, the real Groovy carrier, so the
 * {@code micronaut.graphql.tools.processor.*} properties are set for one compilation and the properties are restored after it.
 *
 * <p>{@link #compile} switches the checks off, to build what the runner starts; {@link #compileChecked} is the compilation of a
 * build with the checks on. The errors of the visitor are errors of the compilation; its warnings go to the standard error
 * stream under Groovy, which is captured for the duration of the compilation.</p>
 */
@CompileStatic
final class GroovyCaseCompiler implements CheckedCaseCompiler {

    private static final String PREFIX = 'micronaut.graphql.tools.processor.'

    @Override
    Language getLanguage() {
        return Language.GROOVY
    }

    @Override
    CompiledCase compile(CorpusCase corpusCase, Path work) {
        return compileWith(corpusCase, work, [(PREFIX + 'checks'): 'false'])
    }

    @Override
    CompiledCase compileChecked(CorpusCase corpusCase, Path work) {
        Path schemaDir = Files.createDirectories(work.resolve('schema'))
        corpusCase.schemaFiles().each { Files.copy(it, schemaDir.resolve(it.fileName.toString())) }
        return compileWith(corpusCase, work, [(PREFIX + 'schema.dirs'): schemaDir.toString()])
    }

    private CompiledCase compileWith(CorpusCase corpusCase, Path work, Map<String, String> options) {
        Path classes = Files.createDirectories(work.resolve('classes'))
        Properties saved = (Properties) System.properties.clone()
        PrintStream savedErr = System.err
        ByteArrayOutputStream captured = new ByteArrayOutputStream()
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        forgetVisitors()
        try {
            System.properties.stringPropertyNames().findAll { it.startsWith(PREFIX) }.each { System.clearProperty(it) }
            options.each { String name, String value -> System.setProperty(name, value) }
            System.setErr(new PrintStream(captured, true, 'UTF-8'))
            CompilerConfiguration configuration = new CompilerConfiguration()
            configuration.targetDirectory = classes.toFile()
            configuration.parameters = true
            CompilationUnit unit = new CompilationUnit(configuration, null, loader)
            corpusCase.sources(Language.GROOVY).each { unit.addSource(it.toFile()) }
            try {
                unit.compile(Phases.OUTPUT)
            } catch (CompilationFailedException e) {
                List<String> errors = ((List<Message>) (e.unit?.errorCollector?.errors ?: [])).collect { text(it) }
                return new CompiledCase(false, classes, errors.empty ? [e.message] : errors, [], warnings(captured))
            }
            return new CompiledCase(true, classes, [], [], warnings(captured))
        } finally {
            System.setErr(savedErr)
            loader.close()
            System.properties = saved
            forgetVisitors()
        }
    }

    /**
     * Micronaut keeps the loaded visitors of a compilation in a thread local until the last transformation of it removes them. A
     * compilation that fails never gets there, and the next compilation in the thread then runs the visitors on the state of the
     * failed one, without a {@code start} (the checks of the failed case, and its schema, would apply to the next case). A build that
     * keeps a compiler thread alive between compilations meets the same thing, which is why the runner clears it between cases.
     */
    private static void forgetVisitors() {
        ['loadedVisitors', 'beanDefinitionBuilders'].each { String name ->
            Field field = TypeElementVisitorTransform.getDeclaredField(name)
            field.accessible = true
            ((ThreadLocal<?>) field.get(null)).remove()
        }
    }

    private static String text(Message message) {
        if (message instanceof SyntaxErrorMessage) {
            return ((SyntaxErrorMessage) message).cause.originalMessage
        }
        return message instanceof SimpleMessage ? ((SimpleMessage) message).message : String.valueOf(message)
    }

    /** the warnings that the standard error stream got: each starts with {@code WARNING:} on a line of its own */
    private static List<String> warnings(ByteArrayOutputStream captured) {
        return captured.toString('UTF-8').split(/(?m)^WARNING: /).toList().drop(1).collect { it.trim() }
    }

}
