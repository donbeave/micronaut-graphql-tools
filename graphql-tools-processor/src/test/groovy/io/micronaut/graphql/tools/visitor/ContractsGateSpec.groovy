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

import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.Phases
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * The verification gate of the metadata channel: what an isolating visitor writes with {@code Element#annotate} is
 * on the bean definition at runtime, and is read without reflection. javac and the Groovy compiler run in process here; KSP
 * is the Kotlin corpus backend (#143), where the same visitor class runs.
 */
class ContractsGateSpec extends Specification {

    @TempDir
    Path work

    def "javac: the annotation that a visitor adds to a class is on its bean definition"() {
        when:
        def result = new VisitorCompilation()
                .option(ProcessorOptions.CHECKS, 'false')
                .source('gate.Marked', 'package gate; public @interface Marked { }')
                .source('gate.Bean', 'package gate; @Marked @jakarta.inject.Singleton public class Bean { }')
                .compile()

        then:
        result.success
        metadata(result.classOutput, 'gate.Bean') == [value: ['java.lang.String'], declaredBy: ['gate.Contract']]
    }

    def "groovy: the annotation that a visitor adds to a class is on its bean definition"() {
        given:
        def classes = Files.createDirectories(work.resolve('groovy-classes'))
        def sources = Files.createDirectories(work.resolve('groovy-src'))
        Files.writeString(sources.resolve('Marked.groovy'), 'package gate\n@interface Marked { }\n')
        Files.writeString(sources.resolve('Bean.groovy'), 'package gate\n@Marked\n@jakarta.inject.Singleton\nclass Bean { }\n')
        def configuration = new CompilerConfiguration()
        configuration.targetDirectory = classes.toFile()
        def loader = new GroovyClassLoader(getClass().classLoader)
        def unit = new CompilationUnit(configuration, null, loader)
        unit.addSource(sources.resolve('Marked.groovy').toFile())
        unit.addSource(sources.resolve('Bean.groovy').toFile())
        Properties saved = (Properties) System.properties.clone()

        when:
        System.setProperty(ProcessorOptions.CHECKS, 'false')
        unit.compile(Phases.OUTPUT)

        then:
        metadata(classes.toFile(), 'gate.Bean') == [value: ['java.lang.String'], declaredBy: ['gate.Contract']]

        cleanup:
        System.properties = saved
        loader.close()
    }

    def "javac: the visitor records the inherited contracts, with the checks off, in the order of the contract names"() {
        when:
        def result = new VisitorCompilation()
                .option(ProcessorOptions.CHECKS, 'false')
                .source('res.BookContract', 'package res; @io.micronaut.graphql.tools.annotation.GraphQLTypeResolver(String.class) public interface BookContract { }')
                .source('res.AuthorContract', 'package res; @io.micronaut.graphql.tools.annotation.GraphQLTypeResolver(Integer.class) public interface AuthorContract { }')
                .source('res.Base', 'package res; public abstract class Base implements AuthorContract { }')
                .source('res.Both', 'package res; @jakarta.inject.Singleton public class Both extends Base implements BookContract { }')
                .source('res.Direct', 'package res; @io.micronaut.graphql.tools.annotation.GraphQLTypeResolver(Long.class) @jakarta.inject.Singleton public class Direct { }')
                .source('res.Plain', 'package res; @jakarta.inject.Singleton public class Plain { }')
                .compile()

        then:
        result.success
        metadata(result.classOutput, 'res.Both') == [value: ['java.lang.Integer', 'java.lang.String'],
                                                     declaredBy: ['res.AuthorContract', 'res.BookContract']]
        metadata(result.classOutput, 'res.Direct') == [value: ['java.lang.Long'], declaredBy: ['res.Direct']]
        metadata(result.classOutput, 'res.Plain') == [value: [], declaredBy: []]
    }

    def "groovy: the visitor records the inherited contracts, with the checks off"() {
        given:
        def classes = Files.createDirectories(work.resolve('contracts-classes'))
        def sources = Files.createDirectories(work.resolve('contracts-src'))
        Files.writeString(sources.resolve('Contract.groovy'), 'package res\n@io.micronaut.graphql.tools.annotation.GraphQLTypeResolver(String)\ninterface Contract { }\n')
        Files.writeString(sources.resolve('Impl.groovy'), 'package res\n@jakarta.inject.Singleton\nclass Impl implements Contract { }\n')
        def configuration = new CompilerConfiguration()
        configuration.targetDirectory = classes.toFile()
        def loader = new GroovyClassLoader(getClass().classLoader)
        def unit = new CompilationUnit(configuration, null, loader)
        unit.addSource(sources.resolve('Contract.groovy').toFile())
        unit.addSource(sources.resolve('Impl.groovy').toFile())
        Properties saved = (Properties) System.properties.clone()

        when:
        System.setProperty(ProcessorOptions.CHECKS, 'false')
        unit.compile(Phases.OUTPUT)

        then:
        metadata(classes.toFile(), 'res.Impl') == [value: ['java.lang.String'], declaredBy: ['res.Contract']]

        cleanup:
        System.properties = saved
        loader.close()
    }

    private static Map<String, List<String>> metadata(File classes, String type) {
        def urls = [classes.toURI().toURL()]
        System.getProperty('java.class.path').split(File.pathSeparator).each { urls.add(new File(it).toURI().toURL()) }
        def loader = new URLClassLoader(urls as URL[], ClassLoader.platformClassLoader)
        def thread = Thread.currentThread()
        def previous = thread.contextClassLoader
        try {
            thread.contextClassLoader = loader
            return (Map<String, List<String>>) loader.loadClass(ContractsGateReader.name).getMethod('read', String).invoke(null, type)
        } finally {
            thread.contextClassLoader = previous
            loader.close()
        }
    }
}
