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
package io.micronaut.graphql.tools.mapping.interfacetype

import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.SchemaMappingDictionaryCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language

class InterfaceListDataFetcherResultSpec extends AbstractTest {

    static final String SPEC_NAME = "InterfaceListDataFetcherResultSpec"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  shelf: Shelf!
}

type Shelf {
  blocks: [Block!]!
  hasNext: Boolean!
}

interface Block {
  id: String!
  kind: BlockKind!
}

type BookListBlock implements Block {
  id: String!
  kind: BlockKind!
  title: String!
  subtitle: String
  books: [Book!]!
}

type BannerBlock implements Block {
  id: String!
  kind: BlockKind!
  imageUrl: String!
}

type Book {
  id: ID!
  title: String
}

enum BlockKind { BOOKS, BANNER }
"""

    void "each element resolves to its object type and passes its local context to the object's resolvers"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = executeQuery("""
{
    shelf {
        hasNext
        blocks {
            __typename
            id
            kind
            ... on BookListBlock {
                title
                subtitle
                books { id title }
            }
            ... on BannerBlock { imageUrl }
        }
    }
}
""")

        then:
        result.errors.isEmpty()
        result.data.shelf == [
                hasNext: false,
                blocks : [
                        [__typename: 'BookListBlock', id: 'bestsellers', kind: 'BOOKS', title: 'Bestsellers',
                         subtitle  : 'localContext=from-bestsellers', books: [[id: '1', title: 'One']]],
                        [__typename: 'BannerBlock', id: 'banner', kind: 'BANNER', imageUrl: 'https://example.com/b.png']
                ]
        ]
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        Shelf shelf() {
            return new Shelf(hasNext: false, blocks: [
                    DataFetcherResult.<Block> newResult()
                            .data(new BookListBlock(id: 'bestsellers', kind: BlockKind.BOOKS, title: 'Bestsellers',
                                    books: [new Book(id: '1', title: 'One')]))
                            .localContext('from-bestsellers')
                            .build(),
                    DataFetcherResult.<Block> newResult()
                            .data(new BannerBlock(id: 'banner', kind: BlockKind.BANNER,
                                    imageUrl: 'https://example.com/b.png'))
                            .build()
            ])
        }
    }

    @GraphQLType
    static class Shelf {
        List<DataFetcherResult<Block>> blocks
        boolean hasNext
    }

    static interface Block {
        String getId()

        BlockKind getKind()
    }

    @GraphQLType
    static class BookListBlock implements Block {
        String id
        BlockKind kind
        String title
        List<Book> books
    }

    @GraphQLType
    static class BannerBlock implements Block {
        String id
        BlockKind kind
        String imageUrl
    }

    @GraphQLType
    static class Book {
        String id
        String title
    }

    @Introspected
    static enum BlockKind {
        BOOKS, BANNER
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLTypeResolver(BookListBlock.class)
    static class BookListBlockResolver {
        String subtitle(BookListBlock block, DataFetchingEnvironment env) {
            return 'localContext=' + env.getLocalContext()
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Factory
    static class DictionaryFactory {
        @Bean
        @Singleton
        SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (schemaMappingDictionary) -> schemaMappingDictionary
                    .registerType("BookListBlock", BookListBlock.class)
                    .registerType("BannerBlock", BannerBlock.class)
        }
    }

}
