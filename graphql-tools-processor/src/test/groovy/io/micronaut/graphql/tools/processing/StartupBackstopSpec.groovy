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
package io.micronaut.graphql.tools.processing

import spock.lang.Specification

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
import io.micronaut.graphql.tools.mapping.GraphQLScalarMapping;
import io.micronaut.graphql.tools.customizer.SchemaMappingDictionaryCustomizer;
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
        startupFails(result, 'union-type-mapping-not-provided', 'Can not detect representation class for type Magazine, member of SearchResult union. '
                + 'Ensure the representation class is registered via '
                + 'io.micronaut.graphql.tools.mapping.SchemaMappingDictionary.', 'Query.search (line 6, column 3)')
    }

    void "a union member mapped to a final class that does not implement the union's Java interface compiles and fails the startup"() {
        when:
        def result = compile(beans('.registerType("Book", Book.class).registerType("Magazine", Magazine.class)', true),
                SOURCES + ['example.Magazine': SOURCES['example.Magazine'].replace('public class Magazine {',
                        'public final class Magazine {')])

        then:
        result.success
        startupFails(result, 'incorrect-member-mapping', 'The union SearchResult is returned as example.SearchResult, but its object type Magazine '
                + 'is mapped to example.Magazine, and no object can be an instance of both example.SearchResult and '
                + 'example.Magazine, so no object the field returns can resolve to Magazine. Map Magazine to a Java '
                + 'type that example.SearchResult can implement or extend.', 'Query.search (line 6, column 3)')
    }

    void "a custom scalar without a mapping compiles and fails the startup"() {
        when:
        def result = compile(beans('.registerType("Book", Book.class).registerType("Magazine", Magazine.class)', false),
                SOURCES + ['example.Magazine': SOURCES['example.Magazine'].replace('public class Magazine {',
                        'public class Magazine implements SearchResult {')])

        then:
        result.success
        startupFails(result, 'scalar-mapping-not-provided', 'The GraphQL scalar Date has no mapping. Register a io.micronaut.graphql.tools.mapping.GraphQLScalarMapping '
                + 'bean with its graphql.schema.GraphQLScalarType and Java classes.', 'Query.today (line 7, column 3)')
    }

    static boolean startupFails(Compilation.Result result, String code, String message, String coordinate) {
        String startup = result.startupManifest()
        assert startup.startsWith('FAILED: [' + code + '] ' + message)
        assert startup.contains('GraphQL coordinate: ' + coordinate)
        return true
    }

}
