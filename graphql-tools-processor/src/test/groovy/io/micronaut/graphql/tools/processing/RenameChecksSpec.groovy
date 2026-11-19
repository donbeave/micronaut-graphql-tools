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

import static ResolverChecksSpec.compilation

/** Binding a field to a member with another name, in the JSR-269 processor (#107). */
class RenameChecksSpec extends Specification {

    static final String SCHEMA = '''schema {
  query: Query
}

type Query {
  book(id: ID!): Book
  books(limit: Int): [Book!]!
}

type Book {
  title: String!
  author: String
  class: String!
}
'''

    static final String BOOK = '''
package example;

import io.micronaut.graphql.tools.annotation.GraphQLField;
import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class Book {
    private final String title;

    public Book(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    @GraphQLField("class")
    public String kind() {
        return "book";
    }
}
'''

    static final String BOOK_RESOLVER = '''
package example;

import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;

@GraphQLTypeResolver(Book.class)
public class BookResolver {
    public String getAuthor(Book book) {
        return "Jane";
    }
}
'''

    void "a @GraphQLField method binds the field of the name it declares"() {
        when:
        def result = compilation(['example.Book': BOOK, 'example.BookResolver': BOOK_RESOLVER.replace('getAuthor', 'author')], SCHEMA).compile()

        then:
        result.success
        result.graphqlErrors.isEmpty()
    }

    void "a member renamed to the name of another member fails with the coordinate and both members"() {
        when:
        def result = compilation(['example.Book': BOOK.replace('@GraphQLField("class")', '@GraphQLField("title")'),
                                  'example.BookResolver': BOOK_RESOLVER.replace('getAuthor', 'author')],
                SCHEMA.replace('  class: String!\n', '')).compile()

        then:
        !result.success
        result.graphqlErrors.size() == 1
        result.graphqlErrors[0].contains('[graphql] Book.title: several members of example.Book can bind the field: '
                + 'example.Book#getTitle(), example.Book#kind().')
    }

    void "a renamed property binds the field of its new name only"() {
        when:
        def result = compilation(['example.Book': BOOK.replace('    public String getTitle() {', '    @GraphQLField("headline")\n    public String getTitle() {'),
                                  'example.BookResolver': BOOK_RESOLVER.replace('getAuthor', 'author')],
                SCHEMA.replace('  author: String\n', '  author: String\n  headline: String!\n')).compile()

        then:
        !result.success
        result.graphqlErrors.size() == 1
        result.graphqlErrors[0].contains('[graphql] Book.title: nothing binds the field')
    }

    void "a coordinate binds a method with another name"() {
        when:
        def result = compilation(['example.Book': BOOK,
                                  'example.BookResolver': BOOK_RESOLVER.replace('public String getAuthor(',
                                          '@io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate("Book.author") public String writer(')],
                SCHEMA).compile()

        then:
        result.success
        result.graphqlErrors.isEmpty()
    }

    void "a getX resolver method binds the field x only with the accessor prefixes option"() {
        when:
        def off = compilation(['example.Book': BOOK, 'example.BookResolver': BOOK_RESOLVER], SCHEMA).compile()
        def on = compilation(['example.Book': BOOK, 'example.BookResolver': BOOK_RESOLVER], SCHEMA).option(ProcessorOptions.RESOLVER_ACCESSOR_PREFIXES, 'true').compile()

        then:
        !off.success
        off.graphqlErrors.any { it.contains('[graphql] Book.author: nothing binds the field') }
        on.success
    }
}
