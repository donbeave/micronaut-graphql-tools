package io.micronaut.graphql.tools.visitor

import io.micronaut.inject.visitor.TypeElementVisitor
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import java.util.stream.Stream

/**
 * What the visitor must not do (D-39, DD-9 and 2.7), as a test: the lookups and the files that break Gradle's isolating
 * contract, static state, and the runtime keys. The sources are read, so that a call on a branch no test reaches is found too.
 */
class VisitorArchitectureSpec extends Specification {

    static final Path SOURCES = Path.of(System.getProperty('visitor.sources'))

    static final List<String> FORBIDDEN = [
            'getProjectDir', 'getClassesOutputPath', 'getClasspathResources', 'getClassElement(', 'getClassElements(',
            'getRequiredClassElement', 'visitMetaInfFile', 'visitGeneratedFile', 'addGeneratedResource', 'AGGREGATING',
            'System.getProperty', 'System.getenv', 'java.lang.reflect', 'Class.forName',
    ]

    @Shared
    GraphQLToolsVisitor visitor = new GraphQLToolsVisitor()

    def "the sources are found"() {
        expect:
        files().size() >= 5
    }

    def "no class of the visitor uses #forbidden"() {
        expect:
        offenders { it.contains(forbidden) }.isEmpty()

        where:
        forbidden << FORBIDDEN
    }

    def "no class of the visitor has a static field that is not final"() {
        expect:
        offenders { it ==~ /\s*(?:(?:private|public|protected)\s+)?static\s+(?!final\b|class\b|record\b|enum\b|interface\b)[^(]*[;=].*/ }.isEmpty()
    }

    def "the visitor is isolating, and claims exactly the three annotations and the two options"() {
        expect:
        visitor.visitorKind == TypeElementVisitor.VisitorKind.ISOLATING
        visitor.supportedAnnotationNames == [
                'io.micronaut.graphql.tools.annotation.GraphQLRootResolver',
                'io.micronaut.graphql.tools.annotation.GraphQLTypeResolver',
                'io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate'] as Set
        visitor.supportedOptions == ['micronaut.graphql.tools.processor.schema.dirs', 'micronaut.graphql.tools.processor.checks'] as Set
    }

    private static List<Path> files() {
        try (Stream<Path> stream = Files.walk(SOURCES)) {
            return stream.filter { it.toString().endsWith('.java') }.collect(Collectors.toList())
        }
    }

    private static List<String> offenders(Closure<Boolean> test) {
        files().findAll { path ->
            path.toFile().readLines().any { line ->
                !line.trim().startsWith('*') && !line.trim().startsWith('//') && !line.trim().startsWith('/*') && test(line)
            }
        }.collect { SOURCES.relativize(it).toString() }
    }
}
