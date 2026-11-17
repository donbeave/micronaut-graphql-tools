package io.micronaut.graphql.tools

import graphql.parser.MultiSourceReader
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.exceptions.BindingFailuresException
import io.micronaut.graphql.tools.exceptions.IncorrectClassMappingException
import io.micronaut.graphql.tools.exceptions.MethodNotFoundException
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language

/**
 * Every field that cannot be bound is reported at once, each failure with its schema coordinate and source location.
 */
class BindingFailuresSpec extends AbstractTest {

    static final String SPEC_NAME = "BindingFailuresSpec"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  book: Book
  count: Int
  missing: String
}

type Book {
  title: String!
  isbn: String!
}
"""

    void "every field that cannot be bound is reported in one error, sorted by coordinate"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof BindingFailuresException

        BindingFailuresException failures = (BindingFailuresException) e.cause
        failures.failures*.class == [MethodNotFoundException, IncorrectClassMappingException, MethodNotFoundException]
        failures.failures*.mappingContext*.coordinate == ['Book.isbn', 'Query.count', 'Query.missing']
        failures.cause.is(failures.failures[0])
        failures.suppressed.toList() == failures.failures.drop(1)
        failures.message == """The schema cannot be bound: 3 errors.
  1) The property or method `isbn` not found in ${Book.name}'s type.
     GraphQL coordinate: Book.isbn (line 14, column 3)
     GraphQL object type: Book
     GraphQL field: isbn
  2) The field is mapped to the incorrect class.
     GraphQL coordinate: Query.count (line 8, column 3)
     GraphQL object type: Query
     GraphQL field: count
     Mapped class: ${Query.name}
     Mapped method: count()
     Provided class: java.lang.String
     Supported classes: int, java.lang.Integer
  3) The method `missing` not found in any root resolvers: [${Query.name}].
     GraphQL coordinate: Query.missing (line 9, column 3)
     GraphQL object type: Query
     GraphQL field: missing"""
    }

    void "a single failure is thrown as is, with its coordinate and source location"() {
        given:
        startContext(SCHEMA.replace('  count: Int\n  missing: String\n', ''), SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof MethodNotFoundException
        e.cause.mappingContext.coordinate == 'Book.isbn'
        e.cause.mappingContext.sourceLocation.line == 12
        e.cause.mappingContext.sourceLocation.column == 3
    }

    void "the source name of the schema file is part of the location"() {
        given:
        TypeDefinitionRegistry registry = new SchemaParser().parse(MultiSourceReader.newMultiSourceReader()
                .string(SCHEMA.replace('  count: Int\n  missing: String\n', ''), 'books.graphqls')
                .build())
        applicationContext = ApplicationContext.builder(['spec.name': SPEC_NAME], 'test')
                .singletons(registry)
                .start()

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause.message.contains('\n  GraphQL coordinate: Book.isbn (books.graphqls, line 12, column 3)\n')
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        Book book() {
            return new Book(title: 'Dune')
        }

        String count() {
            return 'one'
        }
    }

    @GraphQLType
    static class Book {
        String title
    }

    // --- interface fields that nothing binds, on several implementations ---

    void "interface fields that nothing binds are all reported"() {
        given:
        startContext("""
schema {
  query: Query
}

type Query {
  sections: [Section!]!
}

interface Section {
  title: String!
  summary: String!
}

type TextSection implements Section {
  title: String!
  summary: String!
}

type MediaSection implements Section {
  title: String!
  summary: String!
}
""", SPEC_NAME + ".interface")

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof BindingFailuresException
        ((BindingFailuresException) e.cause).failures*.mappingContext*.coordinate ==
                ['MediaSection.summary', 'TextSection.summary']
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".interface")
    @GraphQLRootResolver
    static class SectionQuery {
        List<Section> sections() {
            return []
        }
    }

    static interface Section {
        String getTitle()
    }

    @GraphQLType
    static class TextSection implements Section {
        String title
    }

    @GraphQLType
    static class MediaSection implements Section {
        String title
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".interface")
    @Factory
    static class DictionaryFactory {
        @Bean
        @Singleton
        SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (schemaMappingDictionary) -> schemaMappingDictionary
                    .registerType("TextSection", TextSection.class)
                    .registerType("MediaSection", MediaSection.class)
        }
    }

}
