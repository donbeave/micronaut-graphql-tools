package io.micronaut.graphql.tools.processing

import spock.lang.Specification

import static io.micronaut.graphql.tools.processing.ResolverChecksSpec.BOOK
import static io.micronaut.graphql.tools.processing.ResolverChecksSpec.BOOK_QUERY
import static io.micronaut.graphql.tools.processing.ResolverChecksSpec.BOOK_RESOLVER
import static io.micronaut.graphql.tools.processing.ResolverChecksSpec.SCHEMA
import static io.micronaut.graphql.tools.processing.ResolverChecksSpec.compilation

class FailureClassesSpec extends Specification {

    void "a resolver contract without a bean class fails, and the fields it declares are unbound"() {
        when:
        def result = compilation(['example.ShelfContract': '''
package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public interface ShelfContract {
    String shelf();
}
'''], SCHEMA.replace('books(limit: Int): [Book!]!', 'books(limit: Int): [Book!]!\n  shelf: String')).compile()

        then:
        !result.success
        result.graphqlErrors == [
                'ShelfContract.java:7: [graphql] example.ShelfContract: the resolver contract has no bean class: no bean '
                        + 'of this compilation implements it, so the fields it declares are not bound. Implement it in '
                        + 'a class with a bean annotation such as @Singleton.',
                '[graphql] Query.shelf: no root resolver method binds the field: none of the root resolvers '
                        + '(example.BookQuery) has a public method shelf(). (schema.graphqls:8)',
        ]
    }

    void "an introduction advice interface is its own bean and binds its fields"() {
        when:
        def result = compilation(['example.Shelved': '''
package example;

import io.micronaut.aop.Introduction;

@Introduction
@interface Shelved {
}
''', 'example.ShelfContract': '''
package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@Shelved
@GraphQLRootResolver
public interface ShelfContract {
    String shelf();
}
'''], SCHEMA.replace('books(limit: Int): [Book!]!', 'books(limit: Int): [Book!]!\n  shelf: String')).compile()

        then:
        result.success
        result.graphqlErrors == []
    }

    void "a schema field deleted while its resolver method stays fails in strict mode"() {
        when:
        def result = compilation([:], SCHEMA.replace('  books(limit: Int): [Book!]!\n', '')).compile()

        then:
        !result.success
        result.graphqlErrors == [
                'BookQuery.java:16: [graphql] example.BookQuery#books(java.lang.Integer, '
                        + 'graphql.schema.DataFetchingEnvironment): the resolver method binds no schema field (strict '
                        + 'mode). Remove it, make it non-public, or annotate the methods of the bean that bind fields '
                        + 'with @GraphQLSchemaCoordinate.',
        ]
    }

    void "arguments in another order than the schema's fail on each parameter"() {
        when:
        def result = compilation(['example.BookQuery': BOOK_QUERY.replace('books(Integer limit, DataFetchingEnvironment env)',
                'books(String after, Integer limit, DataFetchingEnvironment env)')],
                SCHEMA.replace('books(limit: Int)', 'books(limit: Int, after: String)')).compile()

        then:
        !result.success
        result.graphqlErrors == [
                'BookQuery.java:16: [graphql] Query.books(limit:): the argument or input field is mapped to the '
                        + 'incorrect class: Int accepts [int, java.lang.Integer], the Java type is java.lang.String. '
                        + '(schema.graphqls:7)',
                'BookQuery.java:16: [graphql] Query.books(after:): the argument or input field is mapped to the '
                        + 'incorrect class: String accepts [java.lang.String], the Java type is java.lang.Integer. '
                        + '(schema.graphqls:7)',
        ]
    }

    void "a field of an object type returning a scalar class fails"() {
        when:
        def result = compilation(['example.BookQuery': BOOK_QUERY.replace('public Book book(String id) {\n        return new Book(id);',
                'public String book(String id) {\n        return id;')]).compile()

        then:
        !result.success
        result.graphqlErrors == [
                'BookQuery.java:12: [graphql] Query.book: the field is mapped to the incorrect class: the object type '
                        + 'Book needs a custom class, the Java type is java.lang.String. (schema.graphqls:6)',
        ]
    }

    void "a list field returning a single object fails"() {
        when:
        def result = compilation(['example.BookQuery': BOOK_QUERY.replace('CompletionStage<List<Book>> books',
                'CompletionStage<Book> books').replace('CompletableFuture.completedFuture(List.of())',
                'CompletableFuture.completedFuture(null)')]).compile()

        then:
        !result.success
        result.graphqlErrors == [
                'BookQuery.java:16: [graphql] Query.books: the field is a list ([Book!]!), but example.Book is not an '
                        + 'Iterable. (schema.graphqls:7)',
        ]
    }

    void "a getter and a @GraphQLField method of one class for the same field fail"() {
        when:
        def result = compilation(['example.Book': BOOK.replace('    public String getTitle() {', '''    @io.micronaut.graphql.tools.annotation.GraphQLField
    public String title() {
        return title;
    }

    public String getTitle() {''')]).compile()

        then:
        !result.success
        result.graphqlErrors == [
                'Book.java:15: [graphql] Book.title: several members of example.Book can bind the field: '
                        + 'example.Book#getTitle(), example.Book#title(). (schema.graphqls:11)',
        ]
    }

    void "two methods for one field fail: root resolvers and type resolvers"() {
        given:
        def otherQuery = '''
package example;

@io.micronaut.graphql.tools.annotation.GraphQLRootResolver
public class OtherQuery {
    public Book book(String id) {
        return null;
    }
}
'''
        def otherResolver = BOOK_RESOLVER.replace('public class BookResolver', 'public class OtherBookResolver')

        when:
        def root = compilation(['example.OtherQuery': otherQuery]).compile()
        def type = compilation(['example.OtherBookResolver': otherResolver]).compile()

        then:
        !root.success
        root.graphqlErrors == [
                'BookQuery.java:12: [graphql] Query.book: several root resolver methods can bind the field: '
                        + 'example.BookQuery#book(java.lang.String), example.OtherQuery#book(java.lang.String). Keep '
                        + 'one, or scope each to its root operation type with @GraphQLSchemaCoordinate. (schema.graphqls:6)',
        ]
        !type.success
        type.graphqlErrors == [
                'BookResolver.java:9: [graphql] Book.author: several type resolver methods can bind the field: '
                        + 'example.BookResolver#author(example.Book), example.OtherBookResolver#author(example.Book). '
                        + '(schema.graphqls:12)',
        ]
    }

    void "wrapper misuse fails with the coordinate and the Java element"() {
        when:
        def result = compilation(['example.BookQuery': query, 'example.Book': BOOK.replace('public String getTitle()', title)])
                .compile()

        then:
        !result.success
        result.graphqlErrors == [error]

        where:
        query                                                                                     | title                                                                                                | error
        BOOK_QUERY.replace('CompletionStage<List<Book>> books', 'CompletionStage books')           | 'public String getTitle()'                                                                           | 'BookQuery.java:16: [graphql] Query.books: java.util.concurrent.CompletionStage must declare the type it wraps (a raw java.util.concurrent.CompletionStage is not allowed). (schema.graphqls:7)'
        BOOK_QUERY                                                                                | 'public java.util.Optional<String> getTitle()'                                                       | 'Book.java:14: [graphql] Book.title: java.util.Optional is only allowed for a nullable GraphQL type, the field type is String!. (schema.graphqls:11)'
        BOOK_QUERY                                                                                | 'public graphql.execution.DataFetcherResult<graphql.execution.DataFetcherResult<String>> getTitle()' | 'Book.java:14: [graphql] Book.title: graphql.execution.DataFetcherResult cannot wrap graphql.execution.DataFetcherResult. Supported wrappers, outermost first: CompletionStage (field level only), Publisher (subscription fields only), DataFetcherResult, Optional (nullable types only), each at most once. (schema.graphqls:11)'
        BOOK_QUERY.replace('public Book book(String id)', 'public org.reactivestreams.Publisher<Book> book(String id)')
                .replace('return new Book(id);', 'return null;')                                 | 'public String getTitle()'                                                                           | 'BookQuery.java:12: [graphql] Query.book: org.reactivestreams.Publisher is only allowed on fields of the subscription type. (schema.graphqls:6)'
    }

}
