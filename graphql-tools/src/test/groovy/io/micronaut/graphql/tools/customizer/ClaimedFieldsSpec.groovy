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
package io.micronaut.graphql.tools.customizer

import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.StaticDataFetcher
import graphql.schema.idl.RuntimeWiring
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.BindingManifest
import io.micronaut.graphql.tools.RuntimeWiringCustomizer
import io.micronaut.graphql.tools.SchemaMappingDictionaryCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.exceptions.InvalidClaimedFieldsException
import io.micronaut.graphql.tools.exceptions.RuntimeWiringOverrideException
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language

class ClaimedFieldsSpec extends AbstractTest {

    static final String SPEC_NAME = "ClaimedFieldsSpec"
    static final String SPEC_NAME_JAVA = "ClaimedFieldsSpecJava"
    static final String SPEC_NAME_EXTENSION = "ClaimedFieldsSpec.extension"
    static final String SPEC_NAME_UNCLAIMED = "ClaimedFieldsSpec.unclaimed"
    static final String SPEC_NAME_STALE = "ClaimedFieldsSpec.stale"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  hello: String
  version: String
  book: Book
}

type Book {
  title: String
  isbn: String
}
"""

    @Language("GraphQL")
    static final String EXTENSION_SCHEMA = """
schema {
  query: Query
}

type Query {
  hello: String
}

type Book {
  title: String
}

extend type Query {
  book: Book
  version: String
}

extend type Book {
  isbn: String
}
"""

    @Language("GraphQL")
    static final String STALE_SCHEMA = SCHEMA + """
interface Node {
  id: ID
}
"""

    void "a field with no Java member that a customizer claims starts and executes"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = executeQuery('{ hello version book { title isbn } }')

        then:
        result.errors.isEmpty()
        result.data == [hello: 'world', version: '1.0', book: [title: 'title', isbn: '978-0']]
    }

    void "a Java customizer claims fields the same way"() {
        given:
        startContext(SCHEMA, SPEC_NAME_JAVA)

        when:
        def result = executeQuery('{ hello version book { title isbn } }')

        then:
        result.errors.isEmpty()
        result.data == [hello: 'world', version: '1.0', book: [title: 'title', isbn: '978-0']]
    }

    void "a claimed field appears in the manifest, a bound one keeps its line"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def lines = applicationContext.getBean(BindingManifest).lines

        then:
        lines.contains("Query.version: String -> claimed ; ${ClaimingCustomizer.name} ; - ; -".toString())
        lines.contains("Book.isbn: String -> claimed ; ${ClaimingCustomizer.name} ; - ; -".toString())
        lines.any { it.startsWith('Query.hello: String -> root ; ') }
    }

    void "an extension field nothing binds names claimedFields() in the failure, and a claim serves it"() {
        when:
        startContext(EXTENSION_SCHEMA, SPEC_NAME_EXTENSION)
        getGraphQLBean()

        then: 'the unclaimed extension fields fail, each with the hint'
        def e = thrown(BeanInstantiationException)
        e.cause.message.contains('GraphQL coordinate: Book.isbn')
        e.cause.message.contains('GraphQL coordinate: Query.version')
        e.cause.message.contains('RuntimeWiringCustomizer#claimedFields()')
        e.cause.message.count('[field-not-bound]') == 2
        !e.cause.message.contains('[field-not-bound] [field-not-bound]')

        when: 'a customizer claims them'
        applicationContext.close()
        startContext(EXTENSION_SCHEMA, SPEC_NAME_EXTENSION, ['claim': true])
        def result = executeQuery('{ hello version book { title isbn } }')

        then:
        result.errors.isEmpty()
        result.data == [hello: 'world', version: '2.0', book: [title: 'title', isbn: '978-1']]
    }

    void "replacing a bound field without claiming it still fails, and names claimedFields()"() {
        given: 'the customizer claims Query.version, but replaces the bound Query.hello through the code registry'
        startContext(SCHEMA, SPEC_NAME_UNCLAIMED)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof RuntimeWiringOverrideException
        e.cause.replacements == [
                "data fetcher of Query.hello, through the code registry (${ReplacesBoundAndClaims.name})".toString()
        ]
        e.cause.message.contains('RuntimeWiringCustomizer#claimedFields()')
    }

    void "a stale claim fails the startup with the coordinate"() {
        given:
        startContext(STALE_SCHEMA, SPEC_NAME_STALE)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof InvalidClaimedFieldsException
        e.cause.problems == [
                "Book.price: Book defines no field price (${StaleClaims.name})".toString(),
                "Missing.field: the schema defines no type Missing (${StaleClaims.name})".toString(),
                "Node.id: Node is not an object type: claim the field of each object type that implements it (${StaleClaims.name})".toString(),
        ]
        e.cause.message.contains('RuntimeWiringCustomizer#claimedFields()')
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    static class ClaimingCustomizer implements RuntimeWiringCustomizer {
        @Override
        Set<FieldCoordinates> claimedFields() {
            return [FieldCoordinates.coordinates('Query', 'version'),
                    FieldCoordinates.coordinates('Book', 'isbn')] as Set
        }

        @Override
        void customize(RuntimeWiring.Builder builder) {
            builder.type('Query') { it.dataFetcher('version', new StaticDataFetcher('1.0')) }
            builder.type('Book') { it.dataFetcher('isbn', new StaticDataFetcher('978-0')) }
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_EXTENSION)
    @Requires(property = 'claim', value = 'true')
    @Singleton
    static class ClaimingExtensionFields implements RuntimeWiringCustomizer {
        @Override
        Set<FieldCoordinates> claimedFields() {
            return [FieldCoordinates.coordinates('Query', 'version'),
                    FieldCoordinates.coordinates('Book', 'isbn')] as Set
        }

        @Override
        void customize(RuntimeWiring.Builder builder) {
            builder.type('Query') { it.dataFetcher('version', new StaticDataFetcher('2.0')) }
            builder.type('Book') { it.dataFetcher('isbn', new StaticDataFetcher('978-1')) }
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_UNCLAIMED)
    @Singleton
    static class ReplacesBoundAndClaims implements RuntimeWiringCustomizer {
        @Override
        Set<FieldCoordinates> claimedFields() {
            return [FieldCoordinates.coordinates('Query', 'version'),
                    FieldCoordinates.coordinates('Book', 'isbn')] as Set
        }

        @Override
        void customize(RuntimeWiring.Builder builder) {
            builder.codeRegistry(GraphQLCodeRegistry.newCodeRegistry()
                    .dataFetcher(FieldCoordinates.coordinates('Query', 'version'), new StaticDataFetcher('1.0'))
                    .dataFetcher(FieldCoordinates.coordinates('Book', 'isbn'), new StaticDataFetcher('978-0'))
                    .dataFetcher(FieldCoordinates.coordinates('Query', 'hello'), new StaticDataFetcher('replaced')))
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_STALE)
    @Singleton
    static class StaleClaims implements RuntimeWiringCustomizer {
        @Override
        Set<FieldCoordinates> claimedFields() {
            return [FieldCoordinates.coordinates('Query', 'version'),
                    FieldCoordinates.coordinates('Book', 'price'),
                    FieldCoordinates.coordinates('Missing', 'field'),
                    FieldCoordinates.coordinates('Node', 'id')] as Set
        }

        @Override
        void customize(RuntimeWiring.Builder builder) {
        }
    }

    @Requires(property = 'spec.name', pattern = 'ClaimedFieldsSpec.*')
    @GraphQLRootResolver
    static class Query {
        String hello() {
            return 'world'
        }

        Book book() {
            return new Book()
        }
    }

    @GraphQLType
    static class Book {
        String title = 'title'
    }

    @Requires(property = 'spec.name', pattern = 'ClaimedFieldsSpec.*')
    @Factory
    static class DictionaryFactory {
        @Bean
        @Singleton
        SchemaMappingDictionaryCustomizer dictionary() {
            return (dictionary) -> dictionary.registerType('Book', Book.class)
        }
    }

}
