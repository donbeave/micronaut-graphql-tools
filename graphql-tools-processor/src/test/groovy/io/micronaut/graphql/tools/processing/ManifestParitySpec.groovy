package io.micronaut.graphql.tools.processing

import spock.lang.Specification

/**
 * The binding manifest the processor writes equals the manifest the library builds at startup for the same code,
 * schema and options. Each case compiles its fixtures with this processor and Micronaut's, as a build does, then starts
 * the compiled application with the library and compares the two manifests.
 *
 * <p><b>The parity rule.</b></p>
 * <ul>
 *     <li>Header: the four lines are the same. The {@code # binder:} line names the version of the jar the code runs
 *     from; here the processor runs from its class directory, so it says {@code unknown} where the library jar says
 *     its version. Built from jars of one release, the headers are identical.</li>
 *     <li>Body: every line the processor writes is a line of the startup manifest, byte for byte. The startup manifest
 *     has additional lines only of the kinds that depend on runtime beans the processor cannot see:
 *     {@code scalar} lines (they name the {@code Coercing} of the scalar's runtime wiring), {@code resolves} lines and
 *     every line of a type bound only through the {@code SchemaMappingDictionary} (object types of unions and
 *     interfaces, and what only they reach), {@code !unusedDictionary} and {@code !unwiredDirective} lines
 *     (directive wirings are runtime beans), and fields that a type resolver of an interface binds. The processor
 *     records each of them in {@code binding-manifest-omitted.txt}, and the test filters the startup manifest by that
 *     record ({@link #omitted}), never by a list of its own; the cases below pin each
 *     kind.</li>
 * </ul>
 */
class ManifestParitySpec extends Specification {

    static final String HEADER_BINDER = '# binder: '

    // ---------------------------------------------------------------- case 1: everything is known at compile time

    static final String SCHEMA = '''schema {
  query: Query
  mutation: Mutation
  subscription: Subscription
}

type Query {
  book(id: ID!): Book
  books(ids: [ID!], first: Int!): [Book!]!
  shelf: Shelf!
  reviews: [Review]
}

type Mutation {
  addBook(input: BookInput!): Book!
}

type Subscription {
  bookAdded: Book!
}

type Book {
  id: ID!
  title: String!
  available: Boolean!
  genre: Genre
  tags: [String!]!
  author: Author
  excerpt(length: Int!): String
}

type Author {
  name: String!
  books: [Book!]!
}

type Shelf {
  label: String!
  size: Int!
}

type Review implements Rated {
  stars: Float!
}

interface Rated {
  stars: Float!
}

extend type Review {
  comment: String
}

enum Genre {
  NOVEL
  POETRY
}

input BookInput {
  title: String!
  genre: Genre
  tags: [String!]
}
'''

    static final Map<String, String> SOURCES = [
            'example.Entity'        : '''
package example;

public abstract class Entity {
    public String getId() {
        return "1";
    }
}
''',
            'example.Book'          : '''
package example;

import io.micronaut.graphql.tools.annotation.GraphQLField;
import io.micronaut.graphql.tools.annotation.GraphQLType;
import java.util.List;

@GraphQLType
public class Book extends Entity {
    public String getTitle() {
        return "title";
    }

    public boolean isAvailable() {
        return true;
    }

    public Genre getGenre() {
        return Genre.NOVEL;
    }

    public List<String> getTags() {
        return List.of();
    }

    public Author getAuthor() {
        return null;
    }

    @GraphQLField
    public String excerpt(int length) {
        return "";
    }
}
''',
            'example.Author'        : '''
package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public interface Author {
    String getName();
}
''',
            'example.AuthorImpl'    : '''
package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType(Author.class)
public class AuthorImpl implements Author {
    public String getName() {
        return "name";
    }
}
''',
            'example.AuthorResolver': '''
package example;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;
import java.util.List;

@Prototype
@GraphQLTypeResolver(Author.class)
public class AuthorResolver {
    public List<Book> books(Author author) {
        return List.of();
    }
}
''',
            'example.Library'       : '''
package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

public final class Library {
    @GraphQLType
    public record Shelf(String label, int size) {
    }
}
''',
            'example.Review'        : '''
package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class Review {
    public double getStars() {
        return 1.0;
    }

    public String getComment() {
        return "comment";
    }
}
''',
            'example.Genre'         : '''
package example;

public enum Genre {
    NOVEL, POETRY
}
''',
            'example.BookInput'     : '''
package example;

import io.micronaut.graphql.tools.annotation.GraphQLInput;
import java.util.List;

@GraphQLInput
public class BookInput {
    private String title;
    private Genre genre;
    private List<String> tags;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Genre getGenre() {
        return genre;
    }

    public void setGenre(Genre genre) {
        this.genre = genre;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }
}
''',
            'example.BookQuery'     : '''
package example;

import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@GraphQLRootResolver
public class BookQuery {
    public Book book(String id) {
        return null;
    }

    public List<Book> books(List<String> ids, int first, DataFetchingEnvironment env) {
        return List.of();
    }

    public CompletionStage<DataFetcherResult<Library.Shelf>> shelf() {
        return CompletableFuture.completedFuture(null);
    }

    public List<? extends Review> reviews() {
        return List.of();
    }
}
''',
            'example.BookMutation'  : '''
package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public class BookMutation {
    public Book addBook(BookInput input) {
        return null;
    }
}
''',
            'example.BookSubscription': '''
package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import org.reactivestreams.Publisher;

@GraphQLRootResolver
public class BookSubscription {
    public Publisher<Book> bookAdded() {
        return null;
    }
}
''',
    ]

    void "the processor's manifest equals the startup manifest: roots, wrappers, wildcards, inherited getters, records, nested classes, contracts, enums, inputs and an interface that is only implemented"() {
        when:
        def result = compile(SCHEMA, SOURCES)
        def startup = result.startupManifest()

        then:
        result.success
        !startup.startsWith('FAILED')
        sameHeader(result.manifest, startup)
        body(result.manifest) == body(startup).findAll { !omitted(it, result.omitted) }
        body(startup).findAll { omitted(it, result.omitted) } == [
                'Boolean → scalar · graphql.scalar.GraphqlBooleanCoercing · - · -',
                'Float → scalar · graphql.scalar.GraphqlFloatCoercing · - · -',
                'ID → scalar · graphql.scalar.GraphqlIDCoercing · - · -',
                'Int → scalar · graphql.scalar.GraphqlIntCoercing · - · -',
                'Rated/Review → resolves · example.Review · - · -',
                'String → scalar · graphql.scalar.GraphqlStringCoercing · - · -',
        ]

        and: 'the lines a reader would check by hand'
        body(result.manifest).containsAll([
                'Book.id: ID! → property · example.Entity#getId() · java.lang.String · java.lang.String',
                'Book.available: Boolean! → property · example.Book#isAvailable() · boolean · boolean',
                'Book.excerpt(length: Int!): String → property · example.Book#excerpt(int) · java.lang.String · java.lang.String',
                'Author → object · example.Author · - · -',
                'Author.books: [Book!]! → resolver · example.AuthorResolver#books(example.Author) · java.util.List<example.Book> · java.util.List<example.Book>',
                'Shelf → object · example.Library$Shelf · - · -',
                'Shelf.size: Int! → property · example.Library$Shelf#size() · int · int',
                'Query.shelf: Shelf! → root · example.BookQuery#shelf() · java.util.concurrent.CompletionStage<graphql.execution.DataFetcherResult<example.Library$Shelf>> · example.Library$Shelf',
                'Query.reviews: [Review] → root · example.BookQuery#reviews() · java.util.List<? extends example.Review> · java.util.List<? extends example.Review>',
                'Subscription.bookAdded: Book! → root · example.BookSubscription#bookAdded() · org.reactivestreams.Publisher<example.Book> · example.Book',
                'BookInput.tags: [String!] → inputField · example.BookInput#tags · java.util.List<java.lang.String> · java.util.List<java.lang.String>',
                'Genre.POETRY → enumValue · example.Genre#POETRY · - · -',
                'Rated → interface · - · - · -',
                'Review.comment: String → property · example.Review#getComment() · java.lang.String · java.lang.String',
        ])
    }

    // ---------------------------------------------------------------- case 2: the diagnostics of a lenient configuration

    static final String LENIENT_SCHEMA = '''schema {
  query: Query
}

type Query {
  book(genre: Genre): Book
  find(filter: Filter): Book
}

type Book {
  title: String!
}

type Orphan {
  name: String
}

enum Genre {
  NOVEL
}

enum Unused {
  A
}

input Filter {
  title: String
}
'''

    static final Map<String, String> LENIENT_SOURCES = [
            'example.Book'        : '''
package example;

@io.micronaut.graphql.tools.annotation.GraphQLType
public class Book {
    public String getTitle() {
        return "title";
    }
}
''',
            'example.BookResolver': '''
package example;

@io.micronaut.graphql.tools.annotation.GraphQLTypeResolver(Book.class)
public class BookResolver {
    public String title(Book book) {
        return "resolved";
    }
}
''',
            'example.Genre'       : '''
package example;

public enum Genre {
    NOVEL, POETRY
}
''',
            'example.Filter'      : '''
package example;

@io.micronaut.graphql.tools.annotation.GraphQLInput
public class Filter {
    private String title;
    private Integer limit;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }
}
''',
            'example.BookQuery'   : '''
package example;

@io.micronaut.graphql.tools.annotation.GraphQLRootResolver
public class BookQuery {
    public Book book(Genre genre) {
        return null;
    }

    public Book find(Filter filter) {
        return null;
    }

    public String helper() {
        return null;
    }
}
''',
    ]

    void "the diagnostic lines are the same: ambiguous, unbound methods, enum constants and input properties, unreachable types"() {
        given:
        def options = [strict: 'false', precedence: 'property-first', allowUnreachable: 'Orphan']

        when:
        def result = compile(LENIENT_SCHEMA, LENIENT_SOURCES, options)
        def startup = result.startupManifest(runtimeProperties(options))

        then:
        result.success
        !startup.startsWith('FAILED')
        sameHeader(result.manifest, startup)
        body(result.manifest) == body(startup).findAll { !omitted(it, result.omitted) }
        body(result.manifest).findAll { it.startsWith('!') } == [
                '!ambiguous Book.title · example.Book#getTitle() | example.BookResolver#title(example.Book)',
                '!unbound example.BookQuery#helper()',
                '!unbound example.Filter#limit',
                '!unbound example.Genre#POETRY',
                '!unreachable Orphan',
                '!unreachable Unused',
        ]
        body(startup).findAll { omitted(it, result.omitted) } == ['String → scalar · graphql.scalar.GraphqlStringCoercing · - · -']
    }

    // ---------------------------------------------------------------- case 3: what only the startup knows

    static final String RUNTIME_SCHEMA = '''schema {
  query: Query
}

type Query {
  search: [SearchResult!]!
  node: Node
  today: Date
}

scalar Date

union SearchResult = Book | Magazine

interface Node {
  id: ID!
}

type Book implements Node {
  id: ID!
  title: String!
  publisher: Publisher
}

type Magazine {
  issue: Int!
}

type Publisher {
  name: String!
}
'''

    static final Map<String, String> RUNTIME_SOURCES = [
            'example.SearchResult'        : '''
package example;

public interface SearchResult {
}
''',
            'example.Node'                : '''
package example;

public interface Node {
}
''',
            'example.Book'                : '''
package example;

@io.micronaut.graphql.tools.annotation.GraphQLType
public class Book implements SearchResult, Node {
    public String getId() {
        return "1";
    }

    public String getTitle() {
        return "title";
    }

    public Publisher getPublisher() {
        return null;
    }
}
''',
            'example.Magazine'            : '''
package example;

@io.micronaut.graphql.tools.annotation.GraphQLType
public class Magazine implements SearchResult {
    public int getIssue() {
        return 1;
    }
}
''',
            'example.Publisher'           : '''
package example;

@io.micronaut.graphql.tools.annotation.GraphQLType
public class Publisher {
    public String getName() {
        return "name";
    }
}
''',
            'example.SearchQuery'         : '''
package example;

import java.time.LocalDate;
import java.util.List;

@io.micronaut.graphql.tools.annotation.GraphQLRootResolver
public class SearchQuery {
    public List<SearchResult> search() {
        return List.of();
    }

    public Node node() {
        return null;
    }

    public LocalDate today() {
        return null;
    }
}
''',
            'example.RuntimeBeans'        : '''
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
    GraphQLScalarMapping date() {
        return GraphQLScalarMapping.of(GraphQLScalarType.newScalar().name("Date")
                .coercing(new DateCoercing()).build(), LocalDate.class);
    }

    @Bean
    @Singleton
    SchemaMappingDictionaryCustomizer dictionary() {
        return dictionary -> dictionary.registerType("Book", Book.class).registerType("Magazine", Magazine.class);
    }
}
''',
            'example.DateCoercing'        : '''
package example;

import graphql.schema.Coercing;
import java.time.LocalDate;

public class DateCoercing implements Coercing<LocalDate, String> {
}
''',
    ]

    void "the startup has more lines only where runtime beans decide: scalars, the dictionary's union and interface members and what they reach"() {
        when:
        def result = compile(RUNTIME_SCHEMA, RUNTIME_SOURCES)
        def startup = result.startupManifest()

        then:
        result.success
        !startup.startsWith('FAILED')
        sameHeader(result.manifest, startup)
        body(result.manifest) == body(startup).findAll { !omitted(it, result.omitted) }
        body(result.manifest) == [
                'Node → interface · example.Node · - · -',
                'Query → object · - · - · -',
                'Query.node: Node → root · example.SearchQuery#node() · example.Node · example.Node',
                'Query.search: [SearchResult!]! → root · example.SearchQuery#search() · java.util.List<example.SearchResult> · java.util.List<example.SearchResult>',
                'Query.today: Date → root · example.SearchQuery#today() · java.time.LocalDate · java.time.LocalDate',
                'SearchResult → union · example.SearchResult · - · -',
        ]
        result.omitted.readLines() == ['# graphql-binding-manifest-omitted 1', 'kind resolves', 'kind scalar',
                                         'kind unusedDictionary', 'kind unwiredDirective', 'type Book', 'type Magazine', 'type Publisher']
        body(startup).findAll { omitted(it, result.omitted) } == [
                'Book → object · example.Book · - · -',
                'Book.id: ID! → property · example.Book#getId() · java.lang.String · java.lang.String',
                'Book.publisher: Publisher → property · example.Book#getPublisher() · example.Publisher · example.Publisher',
                'Book.title: String! → property · example.Book#getTitle() · java.lang.String · java.lang.String',
                'Date → scalar · example.DateCoercing · - · -',
                'ID → scalar · graphql.scalar.GraphqlIDCoercing · - · -',
                'Int → scalar · graphql.scalar.GraphqlIntCoercing · - · -',
                'Magazine → object · example.Magazine · - · -',
                'Magazine.issue: Int! → property · example.Magazine#getIssue() · int · int',
                'Node/Book → resolves · example.Book · - · -',
                'Publisher → object · example.Publisher · - · -',
                'Publisher.name: String! → property · example.Publisher#getName() · java.lang.String · java.lang.String',
                'SearchResult/Book → resolves · example.Book · - · -',
                'SearchResult/Magazine → resolves · example.Magazine · - · -',
                'String → scalar · graphql.scalar.GraphqlStringCoercing · - · -',
        ]
    }

    // ---------------------------------------------------------------- case 4: inherited and generic members

    static final String INHERITED_SCHEMA = '''schema {
  query: Query
}

type Query {
  book(id: ID!): Book
  books(ids: [ID!]!): [Book!]!
}

type Book {
  title: String!
  author: String
}
'''

    static final Map<String, String> INHERITED_SOURCES = [
            'example.Titled'      : '''
package example;

public abstract class Titled<T> {
    public T getTitle() {
        return null;
    }
}
''',
            'example.Book'        : '''
package example;

@io.micronaut.graphql.tools.annotation.GraphQLType
public class Book extends Titled<String> {
}
''',
            'example.BaseQuery'   : '''
package example;

import java.util.List;

public abstract class BaseQuery<T, I> {
    public T book(I id) {
        return null;
    }

    public List<T> books(List<? extends I> ids) {
        return List.of();
    }
}
''',
            'example.BookQuery'   : '''
package example;

@io.micronaut.graphql.tools.annotation.GraphQLRootResolver
public class BookQuery extends BaseQuery<Book, String> {
}
''',
            'example.BaseResolver': '''
package example;

public abstract class BaseResolver<S> {
    public String author(S source) {
        return null;
    }
}
''',
            'example.BookResolver': '''
package example;

@io.micronaut.graphql.tools.annotation.GraphQLTypeResolver(Book.class)
public class BookResolver extends BaseResolver<Book> {
}
''',
    ]

    void "inherited generic members: the bean names a resolver method, the declaring class a getter, the declaration its types"() {
        when:
        def result = compile(INHERITED_SCHEMA, INHERITED_SOURCES)
        def startup = result.startupManifest()

        then:
        result.success
        !startup.startsWith('FAILED')
        sameHeader(result.manifest, startup)
        body(result.manifest) == body(startup).findAll { !omitted(it, result.omitted) }
        body(result.manifest).findAll { it.startsWith('Book.') || it.startsWith('Query.') } == [
                'Book.author: String → resolver · example.BookResolver#author(S) · java.lang.String · java.lang.String',
                'Book.title: String! → property · example.Titled#getTitle() · T · T',
                'Query.book(id: ID!): Book → root · example.BookQuery#book(I) · T · T',
                'Query.books(ids: [ID!]!): [Book!]! → root · example.BookQuery#books(java.util.List<? extends I>) · java.util.List<T> · java.util.List<T>',
        ]
    }

    // ---------------------------------------------------------------- case 6: directives without a wiring

    static final String DIRECTIVE_SCHEMA = '''schema {
  query: Query
}

directive @audit on FIELD_DEFINITION

type Query {
  greeting: String @audit
}
'''

    static final Map<String, String> DIRECTIVE_SOURCES = [
            'example.GreetingQuery': '''
package example;

@io.micronaut.graphql.tools.annotation.GraphQLRootResolver
public class GreetingQuery {
    public String greeting() {
        return "hello";
    }
}
''',
    ]

    void "an applied directive without a wiring: the startup fails in strict mode, and its !unwiredDirective line is left to the startup"() {
        when: 'directive wirings are runtime beans: javac cannot tell whether @audit has one'
        def result = compile(DIRECTIVE_SCHEMA, DIRECTIVE_SOURCES)

        then:
        result.success
        result.omitted.readLines().contains('kind unwiredDirective')
        result.startupManifest().startsWith('FAILED: The schema applies directives that have no wiring: @audit (Query.greeting).')

        when:
        def options = [strict: 'false']
        def lenient = compile(DIRECTIVE_SCHEMA, DIRECTIVE_SOURCES, options)
        def startup = lenient.startupManifest(runtimeProperties(options))

        then:
        lenient.success
        !startup.startsWith('FAILED')
        sameHeader(lenient.manifest, startup)
        body(lenient.manifest) == body(startup).findAll { !omitted(it, lenient.omitted) }
        body(startup).findAll { omitted(it, lenient.omitted) } == [
                '!unwiredDirective audit',
                'String → scalar · graphql.scalar.GraphqlStringCoercing · - · -',
        ]
    }

    // ---------------------------------------------------------------- helpers

    /**
     * The parity rule, read from the record the processor wrote next to its manifest, never from a list the test
     * writes.
     *
     * @param line   a line of the startup manifest
     * @param record the content of {@code binding-manifest-omitted.txt}
     * @return whether the processor left the line out, as its record says: a line kind it never writes ({@code kind}),
     * a line of a type it left out ({@code type}), of a field ({@code field}), or an {@code !unbound} line it could not
     * decide ({@code unbound})
     */
    static boolean omitted(String line, String record) {
        return record.readLines().findAll { !it.startsWith('#') }.any { String entry ->
            String kind = entry.substring(0, entry.indexOf(' '))
            String value = entry.substring(entry.indexOf(' ') + 1)
            switch (kind) {
                case 'kind':
                    return line.startsWith('!' + value + ' ') || line.contains(" → ${value} · ")
                case 'type':
                    return line.startsWith(value + ' → ') || line.startsWith(value + '.') || line.startsWith(value + '/')
                            || line.startsWith('!ambiguous ' + value + '.')
                case 'field':
                    return line.startsWith(value + '(') || line.startsWith(value + ':') || line.startsWith('!ambiguous ' + value + ' ')
                case 'unbound':
                    return line == '!unbound ' + value
                default:
                    throw new IllegalStateException('unknown entry in the omitted record: ' + entry)
            }
        }
    }

    static Compilation.Result compile(String schema, Map<String, String> sources, Map<String, String> options = [:]) {
        def compilation = new Compilation().schema('schema.graphqls', schema).withMicronaut()
        sources.each { name, source -> compilation.source(name, source) }
        options.each { name, value -> compilation.option(ProcessorOptions.PREFIX + name, value) }
        return compilation.compile()
    }

    static Map<String, Object> runtimeProperties(Map<String, String> options) {
        Map<String, Object> result = [:]
        options.each { name, value -> result.put('micronaut.graphql.tools.' + name, value) }
        return result
    }

    static List<String> body(String manifest) {
        return manifest.readLines().findAll { !it.startsWith('#') }
    }

    static boolean sameHeader(String processor, String startup) {
        List<String> processorHeader = processor.readLines().findAll { it.startsWith('#') }
        List<String> startupHeader = startup.readLines().findAll { it.startsWith('#') }
        assert processorHeader.size() == 4
        assert processorHeader.findAll { !it.startsWith(HEADER_BINDER) } == startupHeader.findAll { !it.startsWith(HEADER_BINDER) }
        // the processor runs from its class directory here, which has no jar manifest to read the version from
        assert processorHeader.find { it.startsWith(HEADER_BINDER) } == HEADER_BINDER + 'micronaut-graphql-tools unknown'
        assert startupHeader.find { it.startsWith(HEADER_BINDER) }.startsWith(HEADER_BINDER + 'micronaut-graphql-tools ')
        return true
    }

}
