package io.micronaut.graphql.tools.processing

import spock.lang.Specification

/**
 * The drift the processor cannot decide, because runtime beans decide it, passes the compilation and fails the strict
 * startup. A build catches it with a test that starts the application context and builds the schema (gets the
 * {@code GraphQL} or {@code BindingManifest} bean) in strict mode, which is what each case does after compiling with
 * this processor and Micronaut's:
 * <ul>
 *     <li>a union member or interface implementation without a Java class (the {@code SchemaMappingDictionary} has no
 *     entry), so the abstract type cannot resolve to it;</li>
 *     <li>a member mapped to a class that is not an instance of the Java interface the field declares, so no object can
 *     resolve to it;</li>
 *     <li>a custom scalar without a {@code GraphQLScalarMapping} bean.</li>
 * </ul>
 * <p>Directive wirings are checked by the library's directive support, not here. A {@code null} for a non-null field
 * and the class an object of a union actually has are values, which only a request shows; graphql-java reports
 * them as errors of that request.</p>
 */
class StartupBackstopSpec extends Specification {

    static final String SCHEMA = '''schema {
  query: Query
}

type Query {
  search: [SearchResult!]!
  today: Date
}

scalar Date

union SearchResult = Book | Magazine

type Book {
  title: String!
}

type Magazine {
  issue: Int!
}
'''

    static final Map<String, String> SOURCES = [
            'example.SearchResult': 'package example; public interface SearchResult { }',
            'example.Book'        : '''
package example;

@io.micronaut.graphql.tools.annotation.GraphQLType
public class Book implements SearchResult {
    public String getTitle() {
        return "title";
    }
}
''',
            'example.Magazine'    : '''
package example;

@io.micronaut.graphql.tools.annotation.GraphQLType
public class Magazine {
    public int getIssue() {
        return 1;
    }
}
''',
            'example.SearchQuery' : '''
package example;

import java.time.LocalDate;
import java.util.List;

@io.micronaut.graphql.tools.annotation.GraphQLRootResolver
public class SearchQuery {
    public List<SearchResult> search() {
        return List.of();
    }

    public LocalDate today() {
        return null;
    }
}
''',
            'example.DateCoercing': '''
package example;

public class DateCoercing implements graphql.schema.Coercing<java.time.LocalDate, String> {
}
''',
    ]

    static String beans(String dictionary, boolean scalar) {
        return """
package example;

import graphql.schema.GraphQLScalarType;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.graphql.tools.GraphQLScalarMapping;
import io.micronaut.graphql.tools.SchemaMappingDictionaryCustomizer;
import jakarta.inject.Singleton;
import java.time.LocalDate;

@Factory
public class RuntimeBeans {
    @Bean
    @Singleton
    SchemaMappingDictionaryCustomizer dictionary() {
        return dictionary -> dictionary${dictionary};
    }
${scalar ? '''
    @Bean
    @Singleton
    GraphQLScalarMapping date() {
        return GraphQLScalarMapping.of(GraphQLScalarType.newScalar().name("Date").coercing(new DateCoercing()).build(),
                LocalDate.class);
    }
''' : ''}}
"""
    }

    static Compilation.Result compile(String runtimeBeans, Map<String, String> sources = SOURCES) {
        def compilation = new Compilation().schema('schema.graphqls', SCHEMA).withMicronaut()
        (sources + ['example.RuntimeBeans': runtimeBeans]).each { name, source -> compilation.source(name, source) }
        return compilation.compile()
    }

    void "the fixture compiles and starts when the runtime beans match the schema"() {
        when:
        def result = compile(beans('.registerType("Book", Book.class).registerType("Magazine", Magazine.class)', true),
                SOURCES + ['example.Magazine': SOURCES['example.Magazine'].replace('public class Magazine {',
                        'public class Magazine implements SearchResult {')])

        then:
        result.success
        !result.startupManifest().startsWith('FAILED')
    }

    void "a union member without a Java class compiles and fails the startup"() {
        when:
        def result = compile(beans('.registerType("Book", Book.class)', true))

        then:
        result.success
        startupFails(result, 'Can not detect representation class for type Magazine, member of SearchResult union. '
                + 'Ensure the representation class is registered via '
                + 'io.micronaut.graphql.tools.SchemaMappingDictionary.', 'Query.search (line 6, column 3)')
    }

    void "a union member mapped to a class that does not implement the union's Java interface compiles and fails the startup"() {
        when:
        def result = compile(beans('.registerType("Book", Book.class).registerType("Magazine", Magazine.class)', true))

        then:
        result.success
        startupFails(result, 'The union SearchResult is returned as example.SearchResult, but its object type Magazine '
                + 'is mapped to example.Magazine, which is not a example.SearchResult, so no object the field returns '
                + 'can resolve to Magazine.', 'Query.search (line 6, column 3)')
    }

    void "a custom scalar without a mapping compiles and fails the startup"() {
        when:
        def result = compile(beans('.registerType("Book", Book.class).registerType("Magazine", Magazine.class)', false),
                SOURCES + ['example.Magazine': SOURCES['example.Magazine'].replace('public class Magazine {',
                        'public class Magazine implements SearchResult {')])

        then:
        result.success
        startupFails(result, 'The GraphQL scalar Date has no mapping. Register a io.micronaut.graphql.tools.GraphQLScalarMapping '
                + 'bean with its graphql.schema.GraphQLScalarType and Java classes.', 'Query.today (line 7, column 3)')
    }

    static boolean startupFails(Compilation.Result result, String message, String coordinate) {
        String startup = result.startupManifest()
        assert startup.startsWith('FAILED: ' + message)
        assert startup.contains('GraphQL coordinate: ' + coordinate)
        return true
    }

}
