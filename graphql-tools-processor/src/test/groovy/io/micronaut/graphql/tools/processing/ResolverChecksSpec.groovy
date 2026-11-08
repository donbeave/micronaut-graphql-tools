package io.micronaut.graphql.tools.processing

import spock.lang.Specification

/**
 * The checks of resolver beans against the schema: every field has exactly one method or property, methods take the
 * arguments in the schema's order with matching types, return types fit (wrappers, lists, built-in scalars),
 * properties and type resolvers follow the precedence, and schema coordinates are valid and bound. The processor
 * rejects what the startup rejects for the same code and options, and accepts what it accepts.
 */
class ResolverChecksSpec extends Specification {

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
}
'''

    static final String BOOK = '''
package example;

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
}
'''

    static final String BOOK_QUERY = '''
package example;

import graphql.schema.DataFetchingEnvironment;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@GraphQLRootResolver
public class BookQuery {
    public Book book(String id) {
        return new Book(id);
    }

    public CompletionStage<List<Book>> books(Integer limit, DataFetchingEnvironment env) {
        return CompletableFuture.completedFuture(List.of());
    }
}
'''

    static final String BOOK_RESOLVER = '''
package example;

import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;
import java.util.Optional;

@GraphQLTypeResolver(Book.class)
public class BookResolver {
    public Optional<String> author(Book book) {
        return Optional.empty();
    }
}
'''

    static Compilation compilation(Map<String, String> sources, String schema = SCHEMA) {
        def defaults = ['example.Book': BOOK, 'example.BookQuery': BOOK_QUERY, 'example.BookResolver': BOOK_RESOLVER]
        def compilation = new Compilation().schema('schema.graphqls', schema)
        (defaults + sources).each { name, source ->
            if (source != null) {
                compilation.source(name, source)
            }
        }
        return compilation
    }

    void "matching resolvers, a model and a type resolver compile"() {
        when:
        def result = compilation([:]).compile()

        then:
        result.success
        result.graphqlErrors.isEmpty()
    }

    void "a root field without a resolver method fails, naming the field, the schema line and the root resolvers"() {
        when:
        def result = compilation([:], SCHEMA.replace('books(limit: Int): [Book!]!', 'books(limit: Int): [Book!]!\n  shelf: String'))
                .compile()

        then:
        !result.success
        result.graphqlErrors == ['[graphql] Query.shelf: no root resolver method binds the field: none of the root '
                                         + 'resolvers (example.BookQuery) has a public method shelf(). (schema.graphqls:8)']
    }

    void "an object type field without a property or type resolver method fails"() {
        when:
        def result = compilation(['example.BookResolver': null]).compile()

        then:
        !result.success
        result.graphqlErrors == ['Book.java:7: [graphql] Book.author: nothing binds the field: example.Book has no '
                                         + 'property or @GraphQLField method author, and no type resolver of '
                                         + 'example.Book has a method author(example.Book source). (schema.graphqls:12)']
    }

    void "a resolver contract that no bean implements fails"() {
        when:
        def result = compilation(['example.ShelfContract': '''
package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public interface ShelfContract {
    String shelf();
}
''']).compile()

        then:
        !result.success
        result.graphqlErrors.contains('ShelfContract.java:7: [graphql] example.ShelfContract: the resolver contract has '
                + 'no bean class: no bean of this compilation implements it, so the fields it declares are not bound. '
                + 'Implement it in a class with a bean annotation such as @Singleton.')
    }

    void "a contract implemented by a bean binds; one implemented by two beans fails"() {
        given:
        def contract = '''
package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate;

@GraphQLRootResolver
public interface BookQueryContract {
    @GraphQLSchemaCoordinate("Query.book")
    Book book(String id);

    @GraphQLSchemaCoordinate("Query.books")
    java.util.List<Book> books(Integer limit);
}
'''
        def implementation = { String name ->
            """
package example;

@jakarta.inject.Singleton
public class ${name} implements BookQueryContract {
    public Book book(String id) {
        return null;
    }

    public java.util.List<Book> books(Integer limit) {
        return null;
    }

    public String helper() {
        return null;
    }
}
"""
        }

        when:
        def one = compilation(['example.BookQuery': implementation('BookQuery'), 'example.BookQueryContract': contract])
                .compile()
        def two = compilation(['example.BookQuery': implementation('BookQuery'), 'example.BookQueryContract': contract,
                               'example.OtherBookQuery': implementation('OtherBookQuery')]).compile()

        then:
        one.success
        !two.success
        two.graphqlErrors.contains('BookQuery.java:5: [graphql] example.BookQueryContract: the resolver contract has '
                + 'several bean classes (example.BookQuery, example.OtherBookQuery), so each of its fields has several '
                + 'resolver methods. Exactly one bean may implement it.')
        two.graphqlErrors.any { it.contains('Query.book: several root resolver methods can bind the field') }
    }

    void "strict mode: a public method of a resolver bean that binds no field fails; Object methods, static and non-public methods do not count"() {
        given:
        def query = BOOK_QUERY.replace('public Book book(String id) {', '''public String helper(String value) {
        return value;
    }

    public static String util() {
        return null;
    }

    String internal() {
        return null;
    }

    @Override
    public String toString() {
        return "BookQuery";
    }

    public Book book(String id) {''')

        when:
        def strict = compilation(['example.BookQuery': query]).compile()
        def lenient = compilation(['example.BookQuery': query]).option(ProcessorOptions.STRICT, 'false').compile()

        then:
        !strict.success
        strict.graphqlErrors == ['BookQuery.java:12: [graphql] example.BookQuery#helper(java.lang.String): the resolver '
                                         + 'method binds no schema field (strict mode). Remove it, make it non-public, '
                                         + 'or annotate the methods of the bean that bind fields with '
                                         + '@GraphQLSchemaCoordinate.']
        lenient.success
    }

    void "wrong number of parameters fails with the expected signature"() {
        when:
        def result = compilation(['example.BookQuery': BOOK_QUERY.replace('public Book book(String id)',
                'public Book book(String id, String locale)')]).compile()

        then:
        !result.success
        result.graphqlErrors == ['BookQuery.java:12: [graphql] Query.book: wrong number of parameters: '
                                         + 'example.BookQuery#book(java.lang.String, java.lang.String) takes 2 besides '
                                         + 'DataFetchingEnvironment, the field needs 1: book(ID id). (schema.graphqls:6)']
    }

    void "a parameter of the wrong type fails on the parameter, naming the argument"() {
        when:
        def result = compilation(['example.BookQuery': BOOK_QUERY.replace('books(Integer limit,', 'books(String limit,')])
                .compile()

        then:
        !result.success
        result.graphqlErrors == ['BookQuery.java:16: [graphql] Query.books(limit:): the argument or input field is mapped '
                                         + 'to the incorrect class: Int accepts [int, java.lang.Integer], the Java type is '
                                         + 'java.lang.String. (schema.graphqls:7)']
    }

    void "a type resolver method whose first parameter is not the source object fails"() {
        when:
        def result = compilation(['example.BookResolver': BOOK_RESOLVER.replace('author(Book book)', 'author(String book)')])
                .compile()

        then:
        !result.success
        result.graphqlErrors.any { it.contains('[graphql] Book.author: the first parameter of example.BookResolver#author('
                + 'java.lang.String) must be the source object, example.Book, not java.lang.String.') }
    }

    void "a scalar field returning a class of another scalar family fails"() {
        when:
        def result = compilation(['example.Book': BOOK.replace('public String getTitle()', 'public Long getTitle()')
                .replace('return title;', 'return 1L;')]).compile()

        then:
        !result.success
        result.graphqlErrors == ['Book.java:14: [graphql] Book.title: the field is mapped to the incorrect class: String '
                                         + 'accepts [java.lang.String], the Java type is java.lang.Long. (schema.graphqls:11)']
    }

    void "wrapper rules: a raw CompletionStage, a CompletionStage in a list, Optional on a non-null field, a nested DataFetcherResult and a Publisher off the subscription type fail"() {
        when:
        def result = compilation(['example.BookQuery': query, 'example.Book': BOOK.replace('public String getTitle()', title)])
                .compile()

        then:
        !result.success
        result.graphqlErrors.size() == 1
        result.graphqlErrors[0].contains(message)

        where:
        query                                                                                                     | title                                                                     | message
        BOOK_QUERY.replace('CompletionStage<List<Book>> books', 'CompletionStage books')                           | 'public String getTitle()'                                                | 'Query.books: java.util.concurrent.CompletionStage must declare the type it wraps (a raw java.util.concurrent.CompletionStage is not allowed).'
        BOOK_QUERY.replace('CompletionStage<List<Book>> books', 'List<CompletionStage<Book>> books').replace('CompletableFuture.completedFuture(List.of())', 'null') | 'public String getTitle()'                                 | 'Query.books: CompletionStage is not allowed inside a list'
        BOOK_QUERY                                                                                                | 'public java.util.Optional<String> getTitle()'                            | 'Book.title: java.util.Optional is only allowed for a nullable GraphQL type, the field type is String!.'
        BOOK_QUERY                                                                                                | 'public graphql.execution.DataFetcherResult<graphql.execution.DataFetcherResult<String>> getTitle()' | 'Book.title: graphql.execution.DataFetcherResult cannot wrap graphql.execution.DataFetcherResult.'
        BOOK_QUERY.replace('public Book book(String id)', 'public org.reactivestreams.Publisher<Book> book(String id)').replace('return new Book(id);', 'return null;') | 'public String getTitle()' | 'Query.book: org.reactivestreams.Publisher is only allowed on fields of the subscription type.'
    }

    void "wrappers the runtime unwraps are accepted: CompletionStage, DataFetcherResult per list element, Optional, Publisher on the subscription type"() {
        when:
        def result = compilation([
                'example.BookQuery'       : BOOK_QUERY.replace('CompletionStage<List<Book>> books',
                        'CompletionStage<List<graphql.execution.DataFetcherResult<Book>>> books'),
                'example.BookSubscription': '''
package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import org.reactivestreams.Publisher;

@GraphQLRootResolver
public class BookSubscription {
    public Publisher<Book> published() {
        return null;
    }
}
'''], SCHEMA.replace('schema {\n  query: Query\n', 'schema {\n  query: Query\n  subscription: Subscription\n')
                + 'type Subscription {\n  published: Book\n}\n').compile()

        then:
        result.success
    }

    void "a field of the subscription type that does not return a Publisher fails"() {
        when:
        def result = compilation(['example.BookSubscription': '''
package example;

@io.micronaut.graphql.tools.annotation.GraphQLRootResolver
public class BookSubscription {
    public Book published() {
        return null;
    }
}
'''], SCHEMA.replace('schema {\n  query: Query\n', 'schema {\n  query: Query\n  subscription: Subscription\n')
                + 'type Subscription {\n  published: Book\n}\n').compile()

        then:
        !result.success
        result.graphqlErrors.any { it.contains('Subscription.published: a field of the subscription type must return '
                + 'org.reactivestreams.Publisher (optionally inside a CompletionStage), found example.Book.') }
    }

    void "a primitive for a nullable built-in scalar argument fails in strict mode only; for a non-null one it is accepted"() {
        given:
        def query = BOOK_QUERY.replace('books(Integer limit,', 'books(int limit,')

        when:
        def strict = compilation(['example.BookQuery': query]).compile()
        def lenient = compilation(['example.BookQuery': query]).option(ProcessorOptions.STRICT, 'false').compile()
        def nonNull = compilation(['example.BookQuery': query], SCHEMA.replace('limit: Int', 'limit: Int!')).compile()

        then:
        !strict.success
        strict.graphqlErrors == ['BookQuery.java:16: [graphql] Query.books(limit:): the Java type is the primitive int, '
                                         + 'but the GraphQL type Int is nullable, so null cannot be passed (strict mode). '
                                         + 'Use its wrapper class or make the GraphQL type non-null. (schema.graphqls:7)']
        lenient.success
        nonNull.success
    }

    void "precedence strict: a property and a type resolver method for one field fail"() {
        when:
        def result = compilation(['example.BookResolver': BOOK_RESOLVER.replace('public Optional<String> author(Book book) {',
                '''public String title(Book book) {
        return null;
    }

    public Optional<String> author(Book book) {''')]).compile()

        then:
        !result.success
        result.graphqlErrors == ['BookResolver.java:9: [graphql] Book.title: ambiguous binding: the property '
                                         + 'example.Book#getTitle() and the type resolver method '
                                         + 'example.BookResolver#title(example.Book) can both bind the field. Remove one, '
                                         + 'mark the field with a directive listed in '
                                         + 'micronaut.graphql.tools.resolverDirectives, or set '
                                         + 'micronaut.graphql.tools.precedence. (schema.graphqls:11)']
    }

    void "a resolver directive binds the type resolver; property-first and resolver-first decide; a marked field without resolver fails"() {
        given:
        def resolver = BOOK_RESOLVER.replace('public Optional<String> author(Book book) {', '''public String title(Book book) {
        return null;
    }

    public Optional<String> author(Book book) {''')
        def marked = SCHEMA.replace('title: String!', 'title: String! @computed') + 'directive @computed on FIELD_DEFINITION\n'

        when:
        def withDirective = compilation(['example.BookResolver': resolver], marked)
                .option(ProcessorOptions.RESOLVER_DIRECTIVES, 'computed').compile()
        def propertyFirst = compilation(['example.BookResolver': resolver])
                .option(ProcessorOptions.PRECEDENCE, 'property-first').compile()
        def resolverFirst = compilation(['example.BookResolver': resolver])
                .option(ProcessorOptions.PRECEDENCE, 'resolver-first').compile()
        def markedWithoutResolver = compilation([:], marked)
                .option(ProcessorOptions.RESOLVER_DIRECTIVES, 'computed').compile()

        then:
        withDirective.success
        propertyFirst.success
        resolverFirst.success
        !markedWithoutResolver.success
        markedWithoutResolver.graphqlErrors == ['Book.java:14: [graphql] Book.title: the field carries @computed, which '
                                                        + 'needs a type resolver method, but only the property '
                                                        + 'example.Book#getTitle() can bind it. Add a type resolver method '
                                                        + 'for example.Book. (schema.graphqls:11)']
    }

    void "two root resolver methods for one field fail once, naming both"() {
        when:
        def result = compilation(['example.OtherQuery': '''
package example;

@io.micronaut.graphql.tools.annotation.GraphQLRootResolver
public class OtherQuery {
    public Book book(String id) {
        return null;
    }
}
''']).compile()

        then:
        !result.success
        // one finding, reported once; the methods are not also reported as binding no field (strict mode)
        result.graphqlErrors == ['BookQuery.java:12: [graphql] Query.book: several root resolver methods can bind the '
                                         + 'field: example.BookQuery#book(java.lang.String), example.OtherQuery#book(java.lang.String). '
                                         + 'Keep one, or scope each to its root operation type with @GraphQLSchemaCoordinate. '
                                         + '(schema.graphqls:6)']
    }

    void "a schema coordinate is validated: unknown field, method name, root resolver on another type, bean that is no resolver"() {
        when:
        def result = compilation(['example.BookQuery': BOOK_QUERY
                .replace('public Book book(String id)', '@io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate("' + coordinate + '") public Book book(String id)')
                .replace('public CompletionStage<List<Book>> books', '@io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate("Query.books") public CompletionStage<List<Book>> books')])
                .compile()

        then:
        !result.success
        result.graphqlErrors.any { it.contains("[graphql] ${coordinate}: @GraphQLSchemaCoordinate on example.BookQuery#book(java.lang.String): ${message}") }

        where:
        coordinate   | message
        'Query.shelf' | 'the type Query has no field shelf.'
        'Query.books' | 'the field name books differs from the method name book.'
        'Mutation.book' | 'the schema has no type Mutation.'
        'Query'       | 'the value is not a schema coordinate of the form Type.field.'
    }

    void "a schema coordinate on a bean that is neither resolver fails"() {
        when:
        def result = compilation(['example.Helper': '''
package example;

@jakarta.inject.Singleton
public class Helper {
    @io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate("Query.book")
    public Book book(String id) {
        return null;
    }
}
''']).compile()

        then:
        !result.success
        result.graphqlErrors == ['Helper.java:7: [graphql] Query.book: @GraphQLSchemaCoordinate on '
                                         + 'example.Helper#book(java.lang.String): the bean is neither a '
                                         + '@GraphQLRootResolver nor a @GraphQLTypeResolver.']
    }

    void "a type resolver method with a coordinate for a type mapped to another class is not bound and fails"() {
        when:
        def result = compilation(['example.Novel'        : '''
package example;

@io.micronaut.graphql.tools.annotation.GraphQLType
public class Novel {
    public String getTitle() {
        return null;
    }
}
''', 'example.BookResolver': BOOK_RESOLVER.replace('@GraphQLTypeResolver(Book.class)', '@GraphQLTypeResolver(Novel.class)')
                .replace('author(Book book)', 'author(Novel book)')
                .replace('public Optional<String> author', '@io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate("Book.author") public Optional<String> author')])
                .compile()

        then:
        !result.success
        result.graphqlErrors.contains('BookResolver.java:9: [graphql] Book.author: @GraphQLSchemaCoordinate on '
                + 'example.BookResolver#author(example.Novel): the method does not bind Book.author (Book is mapped to '
                + 'example.Book).')
    }

    void "a coordinate scopes a root method to its operation type; an un-annotated method on two operation types fails in strict mode"() {
        given:
        def schema = SCHEMA.replace('schema {\n  query: Query\n', 'schema {\n  query: Query\n  mutation: Mutation\n') +
                'type Mutation {\n  book(id: ID!): Book\n}\n'

        when:
        def shared = compilation([:], schema).compile()
        def lenient = compilation([:], schema).option(ProcessorOptions.STRICT, 'false').compile()

        then:
        !shared.success
        shared.graphqlErrors == ['BookQuery.java:12: [graphql] example.BookQuery#book(java.lang.String): the root resolver '
                                         + 'method binds fields of several root operation types (Mutation.book, '
                                         + 'Query.book) (strict mode). Give each operation type its own method with '
                                         + '@GraphQLSchemaCoordinate.']
        lenient.success
    }

    void "a model method annotated @GraphQLField binds a field with arguments"() {
        when:
        def result = compilation(['example.Book': BOOK.replace('public String getTitle() {', '''@io.micronaut.graphql.tools.annotation.GraphQLField
    public String shortTitle(Integer length) {
        return title;
    }

    public String getTitle() {''')], SCHEMA.replace('author: String\n', 'author: String\n  shortTitle(length: Int): String\n'))
                .compile()

        then:
        result.success
    }

    void "a method a generic superclass declares is checked with the type arguments the bean gives it, as Micronaut binds it"() {
        given:
        def base = '''
package example;

import java.util.List;

public abstract class BaseQuery<T, I> {
    public T book(I id) {
        return null;
    }

    public List<T> books(Integer limit) {
        return List.of();
    }
}
'''
        def baseResolver = '''
package example;

public abstract class BaseResolver<S> {
    public String author(S source) {
        return null;
    }
}
'''

        when:
        def matching = compilation(['example.BaseQuery'   : base, 'example.BaseResolver': baseResolver,
                                    'example.BookQuery'   : 'package example; @io.micronaut.graphql.tools.annotation.GraphQLRootResolver public class BookQuery extends BaseQuery<Book, String> { }',
                                    'example.BookResolver': 'package example; @io.micronaut.graphql.tools.annotation.GraphQLTypeResolver(Book.class) public class BookResolver extends BaseResolver<Book> { }'])
                .compile()
        def mismatching = compilation(['example.BaseQuery': base,
                                       'example.BookQuery': 'package example; @io.micronaut.graphql.tools.annotation.GraphQLRootResolver public class BookQuery extends BaseQuery<Book, Integer> { }'])
                .compile()

        then:
        matching.success
        !mismatching.success
        mismatching.graphqlErrors == ['BaseQuery.java:7: [graphql] Query.book(id:): the argument or input field is mapped '
                                              + 'to the incorrect class: ID accepts [java.lang.String], the Java type is '
                                              + 'java.lang.Integer. (schema.graphqls:6)']
    }

    void "classes another processor generates in this compilation under a \$ name, such as AOP proxies of resolver beans, are not resolver beans"() {
        given:
        def proxy = '''
package example;

public class $BookQuery$Intercepted extends BookQuery {
}
'''

        when:
        def generated = compilation([:]).withProcessor(new GeneratingProcessor('example.$BookQuery$Intercepted', proxy)).compile()
        def written = compilation(['example.$BookQuery$Intercepted': proxy]).compile()

        then:
        generated.success
        !written.success
        written.graphqlErrors[0].contains('Query.book: several root resolver methods can bind the field')
    }

    void "a class of the application annotated @Generated, such as generated code, is checked like any other"() {
        given:
        def generatedAnnotation = '@javax.annotation.processing.Generated("some.codegen.Tool")\n'

        when: 'the only root resolver is generated, and takes a parameter the field does not have'
        def only = compilation(['example.BookQuery': BOOK_QUERY.replace('@GraphQLRootResolver\n', '@GraphQLRootResolver\n' + generatedAnnotation)
                .replace('public Book book(String id)', 'public Book book(String id, String locale)')])
                .compile()
        and: 'a second, generated root resolver binds the same field'
        def duplicate = compilation(['example.OtherQuery': 'package example;\n' + generatedAnnotation
                + '@io.micronaut.graphql.tools.annotation.GraphQLRootResolver\npublic class OtherQuery {\n    public Book book(String id) {\n        return null;\n    }\n}\n'])
                .compile()

        then:
        !only.success
        only.graphqlErrors == ['BookQuery.java:13: [graphql] Query.book: wrong number of parameters: '
                                       + 'example.BookQuery#book(java.lang.String, java.lang.String) takes 2 besides '
                                       + 'DataFetchingEnvironment, the field needs 1: book(ID id). (schema.graphqls:6)']
        !duplicate.success
        duplicate.graphqlErrors[0].contains('Query.book: several root resolver methods can bind the field: '
                + 'example.BookQuery#book(java.lang.String), example.OtherQuery#book(java.lang.String).')
    }

    void "a custom scalar does not switch off the check of type resolvers whose type the schema no longer has"() {
        given:
        def schema = SCHEMA.replace('books(limit: Int): [Book!]!', 'books(limit: Int): [Book!]!\n  today: Date') + '\nscalar Date\n'
        def query = BOOK_QUERY.replace('public Book book(String id) {', 'public java.time.LocalDate today() {\n        return null;\n    }\n\n    public Book book(String id) {')
        def magazine = 'package example; @io.micronaut.graphql.tools.annotation.GraphQLType public class Magazine { public String getName() { return null; } }'
        def magazineResolver = '''
package example;

@io.micronaut.graphql.tools.annotation.GraphQLTypeResolver(Magazine.class)
public class MagazineResolver {
    public String issue(Magazine magazine) {
        return null;
    }
}
'''

        when:
        def result = compilation(['example.BookQuery': query, 'example.Magazine': magazine,
                                  'example.MagazineResolver': magazineResolver], schema).compile()

        then:
        !result.success
        result.graphqlErrors == ['MagazineResolver.java:6: [graphql] example.MagazineResolver#issue(example.Magazine): the '
                                         + 'resolver method binds no schema field (strict mode). Remove it, make it non-public, '
                                         + 'or annotate the methods of the bean that bind fields with @GraphQLSchemaCoordinate.']
        result.graphqlNotes == ['[graphql] Date: not checked at compile time, the startup checks it: the Java classes of a '
                                        + 'custom scalar are runtime beans (GraphQLScalarMapping).']
    }

    void "a field an SDL interface declares is skipped only when a type resolver of a Java interface can bind it, and the skip is a note"() {
        given:
        def schema = SCHEMA.replace('type Book {', 'interface Named {\n  name: String\n}\n\ntype Book implements Named {\n  name: String')
        def named = 'package example; public interface Named { }'
        def namedResolver = '''
package example;

@io.micronaut.graphql.tools.annotation.GraphQLTypeResolver(Named.class)
public class NamedResolver {
    public String name(Named named) {
        return null;
    }
}
'''
        def book = BOOK.replace('public class Book {', 'public class Book implements Named {')

        when:
        def unbound = compilation([:], schema).compile()
        def interfaceResolver = compilation(['example.Named': named, 'example.NamedResolver': namedResolver,
                                             'example.Book': book], schema).compile()

        then:
        !unbound.success
        unbound.graphqlErrors == ['Book.java:7: [graphql] Book.name: nothing binds the field: example.Book has no property or '
                                          + '@GraphQLField method name, and no type resolver of example.Book has a method '
                                          + 'name(example.Book source). (schema.graphqls:15)']
        interfaceResolver.success
        interfaceResolver.graphqlNotes == ['[graphql] Book.name: not checked at compile time, the startup checks it: a type '
                                                   + 'resolver of a Java interface of example.Book has a method name and may bind it.']
    }

    void "fields of type extensions are checked like any other field"() {
        when:
        def rootExtension = compilation([:], SCHEMA + '\nextend type Query {\n  extra: String\n}\n').compile()
        def typeExtension = compilation(['example.Book': BOOK.replace('public String getTitle() {',
                'public String getPages() {\n        return null;\n    }\n\n    public String getTitle() {')],
                SCHEMA + '\nextend type Book {\n  pages: Int\n}\n').compile()

        then:
        !rootExtension.success
        rootExtension.graphqlErrors == ['[graphql] Query.extra: no root resolver method binds the field: none of the root '
                                                + 'resolvers (example.BookQuery) has a public method extra(). (schema.graphqls:16)']
        !typeExtension.success
        typeExtension.graphqlErrors == ['Book.java:14: [graphql] Book.pages: the field is mapped to the incorrect class: Int '
                                                + 'accepts [int, java.lang.Integer], the Java type is java.lang.String. '
                                                + '(schema.graphqls:16)']
    }

    void "with sdlDir set, a compilation without any resolver fails instead of checking nothing"() {
        when:
        def result = new Compilation().schema('schema.graphqls', SCHEMA).source('example.Book', BOOK).compile()

        then:
        !result.success
        result.graphqlErrors == ['[graphql] micronaut.graphql.tools.sdlDir is set, but the compilation has no root or type '
                                         + 'resolver to check against the schema. Apply the processor in the module that holds '
                                         + 'the resolver beans, or do not set micronaut.graphql.tools.sdlDir here.']
    }

}
