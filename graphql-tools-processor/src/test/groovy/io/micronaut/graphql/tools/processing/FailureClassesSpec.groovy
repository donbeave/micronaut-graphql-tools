package io.micronaut.graphql.tools.processing

import spock.lang.Specification

import static io.micronaut.graphql.tools.processing.ResolverChecksSpec.BOOK
import static io.micronaut.graphql.tools.processing.ResolverChecksSpec.BOOK_QUERY
import static io.micronaut.graphql.tools.processing.ResolverChecksSpec.BOOK_RESOLVER
import static io.micronaut.graphql.tools.processing.ResolverChecksSpec.SCHEMA
import static io.micronaut.graphql.tools.processing.ResolverChecksSpec.compilation

/**
 * The negative compile corpus: for every class of schema/Java drift that is to fail at compile time, a compilation
 * that has it fails javac with an error that names the schema coordinate, the Java element (javac's {@code File:line}
 * where there is one) and the schema file and line. This spec holds the cases that no other spec of the processor
 * covers with the full message; the table maps each class to its test.
 *
 * <table>
 *     <caption>Failure class → compile-failure test</caption>
 *     <tr><th>Class</th><th>Drift</th><th>Test</th></tr>
 *     <tr><td>missing root method</td><td>a root field no method binds</td>
 *     <td>ResolverChecksSpec "a root field without a resolver method fails, ..."</td></tr>
 *     <tr><td>missing field binding</td><td>a field of an object type nothing binds</td>
 *     <td>ResolverChecksSpec "an object type field without a property or type resolver method fails"</td></tr>
 *     <tr><td>field of an SDL interface</td><td>a field an SDL interface declares, nothing binds it</td>
 *     <td>ResolverChecksSpec "a field an SDL interface declares is skipped only when a type resolver of a Java interface
 *     can bind it, and the skip is a note"</td></tr>
 *     <tr><td>field of a type extension</td><td>{@code extend type} fields, unbound or of the wrong type</td>
 *     <td>ResolverChecksSpec "fields of type extensions are checked like any other field"</td></tr>
 *     <tr><td>generated code</td><td>a duplicate or a drift in a class annotated {@code @Generated}</td>
 *     <td>ResolverChecksSpec "a class of the application annotated @Generated, such as generated code, is checked like any
 *     other"</td></tr>
 *     <tr><td>contract without bean</td><td>an annotated resolver interface no bean implements</td>
 *     <td>{@link #"a resolver contract without a bean class fails, and the fields it declares are unbound"}</td></tr>
 *     <tr><td>unbound resolver method (strict)</td><td>a public method that binds no field</td>
 *     <td>ResolverChecksSpec "strict mode: a public method of a resolver bean that binds no field fails; ..." and
 *     {@link #"a schema field deleted while its resolver method stays fails in strict mode"}</td></tr>
 *     <tr><td>wrong arity</td><td>parameters besides DataFetchingEnvironment</td>
 *     <td>ResolverChecksSpec "wrong number of parameters fails with the expected signature"</td></tr>
 *     <tr><td>argument order</td><td>arguments of different types swapped</td>
 *     <td>{@link #"arguments in another order than the schema's fail on each parameter"}</td></tr>
 *     <tr><td>argument type</td><td>a parameter of another type, also inside lists and input objects</td>
 *     <td>ResolverChecksSpec "a parameter of the wrong type fails on the parameter, naming the argument";
 *     ModelChecksSpec "an enum argument and a list of input objects are checked through"</td></tr>
 *     <tr><td>return type, object</td><td>a field of an object type returning a JDK class</td>
 *     <td>{@link #"a field of an object type returning a scalar class fails"}</td></tr>
 *     <tr><td>return type, scalar</td><td>a class of another scalar family</td>
 *     <td>ResolverChecksSpec "a scalar field returning a class of another scalar family fails"</td></tr>
 *     <tr><td>list vs single</td><td>a list field returning one object, a single field returning a list</td>
 *     <td>{@link #"a list field returning a single object fails"}; ModelChecksSpec "an object type mapped to a JDK
 *     interface or an interface of the compilation without implementation fails, as at startup"</td></tr>
 *     <tr><td>primitive for nullable</td><td>{@code int} for a nullable {@code Int} argument (strict)</td>
 *     <td>ResolverChecksSpec "a primitive for a nullable built-in scalar argument fails in strict mode only; ..."</td></tr>
 *     <tr><td>getter shadows resolver</td><td>a property and a type resolver method for one field</td>
 *     <td>ResolverChecksSpec "precedence strict: a property and a type resolver method for one field fail"; a field
 *     marked as resolver-bound with only a property: ResolverChecksSpec "a resolver directive binds the type resolver;
 *     ..."</td></tr>
 *     <tr><td>two members of one class</td><td>a getter and a {@code @GraphQLField} method for one field</td>
 *     <td>{@link #"a getter and a @GraphQLField method of one class for the same field fail"}</td></tr>
 *     <tr><td>root name on two operation types</td><td>an un-annotated root method serving two types (strict)</td>
 *     <td>ResolverChecksSpec "a coordinate scopes a root method to its operation type; ..."</td></tr>
 *     <tr><td>duplicate binding</td><td>two root methods, two type resolver methods, two beans of a contract</td>
 *     <td>{@link #"two methods for one field fail: root resolvers and type resolvers"}; ResolverChecksSpec "a contract
 *     implemented by a bean binds; one implemented by two beans fails"</td></tr>
 *     <tr><td>one schema type, two Java types</td><td>an object type reached through two classes</td>
 *     <td>ModelChecksSpec "one object type reached through two Java classes fails"</td></tr>
 *     <tr><td>unreachable type</td><td>a type no root reaches</td>
 *     <td>SchemaChecksSpec "an unreachable object type fails the compilation with its coordinate, file and line";
 *     "strict mode: an unused enum, input type and scalar fail; ..."</td></tr>
 *     <tr><td>enum value missing in Java</td><td>a schema value without constant</td>
 *     <td>ModelChecksSpec "a Java enum without a constant for a schema value fails"</td></tr>
 *     <tr><td>enum constant missing in schema</td><td>a constant without schema value (strict)</td>
 *     <td>ModelChecksSpec "strict mode: a Java enum constant without a schema value fails; ..."</td></tr>
 *     <tr><td>input field without property</td><td>an input field the class has no property for</td>
 *     <td>ModelChecksSpec "an input field without a property of the input class fails"</td></tr>
 *     <tr><td>input property without field</td><td>a settable property the input type lacks (strict)</td>
 *     <td>ModelChecksSpec "an input class property without an input field fails in strict mode; ..."</td></tr>
 *     <tr><td>input field type</td><td>a property of another type</td>
 *     <td>ModelChecksSpec "an input field of a type its property cannot take fails"</td></tr>
 *     <tr><td>undeclared directive</td><td>an applied directive without definition</td>
 *     <td>SchemaChecksSpec "an applied directive that the schema does not declare fails the compilation; ..."</td></tr>
 *     <tr><td>wrapper misuse</td><td>raw or nested wrappers, {@code Optional} on {@code !}, {@code Publisher} off the
 *     subscription type, no {@code Publisher} on it</td>
 *     <td>{@link #"wrapper misuse fails with the coordinate and the Java element"}; ResolverChecksSpec "a field of the
 *     subscription type that does not return a Publisher fails"</td></tr>
 *     <tr><td>schema coordinate</td><td>an invalid, misplaced or unbound {@code @GraphQLSchemaCoordinate}</td>
 *     <td>ResolverChecksSpec "a schema coordinate is validated: ...", "a schema coordinate on a bean that is neither
 *     resolver fails", "a type resolver method with a coordinate for a type mapped to another class is not bound and
 *     fails"</td></tr>
 * </table>
 *
 * <p>Not decidable at compile time, and caught by the strict startup instead ({@link StartupBackstopSpec}): union
 * members and interface implementations (the {@code SchemaMappingDictionary}), custom scalars without mapping, and
 * values a resolver returns ({@code null} for a non-null field, the class an abstract type resolves).</p>
 *
 * <p>One member contract per class (a class that implements the contracts of two members of one union, so that it is
 * not clear which member it resolves to) is not checked here: which Java class a member type has is decided by the
 * {@code SchemaMappingDictionary} at startup, which then resolves each object by its exact class.</p>
 */
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
