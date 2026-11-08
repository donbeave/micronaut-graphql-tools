package io.micronaut.graphql.tools.processing

import spock.lang.Specification

/**
 * The checks of model classes, enums, input classes and abstract types against the schema: one Java type per schema
 * type, bean introspections, enum constants in both directions, input class properties in both directions with their
 * types, and Java interfaces for unions and interfaces.
 */
class ModelChecksSpec extends Specification {

    static final String SCHEMA = '''schema {
  query: Query
}

type Query {
  book(id: ID!): Book
  search(filter: BookFilter): [Book!]!
  result: SearchResult
}

type Book {
  title: String!
  genre: Genre!
}

enum Genre {
  FICTION
  POETRY
}

input BookFilter {
  title: String
  genres: [Genre!]
}

union SearchResult = Book
'''

    static final String BOOK = '''
package example;

@io.micronaut.graphql.tools.annotation.GraphQLType
public class Book implements SearchResult {
    public String getTitle() {
        return null;
    }

    public Genre getGenre() {
        return null;
    }
}
'''

    static final String GENRE = '''
package example;

public enum Genre {
    FICTION,
    POETRY
}
'''

    static final String BOOK_FILTER = '''
package example;

import java.util.List;

@io.micronaut.graphql.tools.annotation.GraphQLInput
public class BookFilter {
    private String title;
    private List<Genre> genres;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<Genre> getGenres() {
        return genres;
    }

    public void setGenres(List<Genre> genres) {
        this.genres = genres;
    }
}
'''

    static final String BOOK_QUERY = '''
package example;

import java.util.List;

@io.micronaut.graphql.tools.annotation.GraphQLRootResolver
public class BookQuery {
    public Book book(String id) {
        return null;
    }

    public List<Book> search(BookFilter filter) {
        return null;
    }

    public SearchResult result() {
        return null;
    }
}
'''

    static Compilation compilation(Map<String, String> sources, String schema = SCHEMA) {
        def defaults = ['example.Book'        : BOOK, 'example.Genre': GENRE, 'example.BookFilter': BOOK_FILTER,
                        'example.BookQuery'   : BOOK_QUERY,
                        'example.SearchResult': 'package example; public interface SearchResult { }']
        def compilation = new Compilation().schema('schema.graphqls', schema)
        (defaults + sources).each { name, source ->
            if (source != null) {
                compilation.source(name, source)
            }
        }
        return compilation
    }

    void "matching models, enums, input classes and abstract types compile"() {
        when:
        def result = compilation([:]).compile()

        then:
        result.success
        result.graphqlErrors.isEmpty()
    }

    void "a Java enum without a constant for a schema value fails"() {
        when:
        def result = compilation(['example.Genre': GENRE.replace('FICTION,\n    POETRY', 'FICTION')]).compile()

        then:
        !result.success
        result.graphqlErrors == ['Genre.java:4: [graphql] Genre: the Java enum example.Genre has no constant for the '
                                         + 'schema values [POETRY]. (schema.graphqls:16)']
    }

    void "strict mode: a Java enum constant without a schema value fails; without strict mode it is accepted"() {
        given:
        def genre = GENRE.replace('POETRY', 'POETRY,\n    ESSAY')

        when:
        def strict = compilation(['example.Genre': genre]).compile()
        def lenient = compilation(['example.Genre': genre]).option(ProcessorOptions.STRICT, 'false').compile()

        then:
        !strict.success
        strict.graphqlErrors == ['Genre.java:7: [graphql] Genre: the enum constant example.Genre#ESSAY has no value in '
                                         + 'the schema enum Genre (strict mode). Add the value to the schema or remove '
                                         + 'the constant. (schema.graphqls:16)']
        lenient.success
    }

    void "an enum field mapped to a class that is not an enum fails"() {
        when:
        def result = compilation(['example.Book': BOOK.replace('public Genre getGenre()', 'public String getGenre()')])
                .compile()

        then:
        !result.success
        result.graphqlErrors == ['Book.java:10: [graphql] Book.genre: the field is mapped to the incorrect class: the '
                                         + 'enum Genre needs a Java enum, the Java type is java.lang.String. '
                                         + '(schema.graphqls:13)']
    }

    void "an input field without a property of the input class fails"() {
        when:
        def result = compilation(['example.BookFilter': '''
package example;

@io.micronaut.graphql.tools.annotation.GraphQLInput
public class BookFilter {
    private String title;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
''']).compile()

        then:
        !result.success
        result.graphqlErrors == ['BookFilter.java:5: [graphql] BookFilter.genres: the input class example.BookFilter has '
                                         + 'no property genres. (schema.graphqls:23)']
    }

    void "an input class property without an input field fails in strict mode; strict false, ignore and fail decide as at startup"() {
        given:
        def filter = BOOK_FILTER.replace('    public String getTitle() {', '''    public void setAuthor(String author) {
    }

    public String getTitle() {''')

        when:
        def strict = compilation(['example.BookFilter': filter]).compile()
        def lenient = compilation(['example.BookFilter': filter]).option(ProcessorOptions.STRICT, 'false').compile()
        def ignore = compilation(['example.BookFilter': filter]).option(ProcessorOptions.UNMAPPED_PROPERTIES, 'ignore')
                .compile()
        def fail = compilation(['example.BookFilter': filter]).option(ProcessorOptions.STRICT, 'false')
                .option(ProcessorOptions.UNMAPPED_PROPERTIES, 'fail').compile()

        then:
        !strict.success
        strict.graphqlErrors == ['BookFilter.java:7: [graphql] BookFilter: the input class example.BookFilter has '
                                         + 'properties that the input type does not define, so no request can set them: '
                                         + 'author (strict mode). Remove them, add them to the input type, or set '
                                         + 'micronaut.graphql.tools.input.unmappedProperties=ignore. (schema.graphqls:21)']
        lenient.success
        ignore.success
        !fail.success
        fail.graphqlErrors[0].contains('author (micronaut.graphql.tools.input.unmappedProperties=fail)')
    }

    void "a property settable through the constructor counts; a read-only computed getter does not"() {
        when:
        def result = compilation(['example.BookFilter': '''
package example;

import java.util.List;

@io.micronaut.graphql.tools.annotation.GraphQLInput
public class BookFilter {
    private final String title;
    private final List<Genre> genres;
    private final String locale;

    public BookFilter(String title, List<Genre> genres, String locale) {
        this.title = title;
        this.genres = genres;
        this.locale = locale;
    }

    public String getTitle() {
        return title;
    }

    public List<Genre> getGenres() {
        return genres;
    }

    public String getLocale() {
        return locale;
    }

    public boolean isEmpty() {
        return title == null;
    }
}
''']).compile()

        then:
        !result.success
        result.graphqlErrors.size() == 1
        result.graphqlErrors[0].contains('so no request can set them: locale (strict mode)')
    }

    void "an input field of a type its property cannot take fails"() {
        when:
        def result = compilation(['example.BookFilter': BOOK_FILTER.replace('private String title;', 'private Integer title;')
                .replace('public String getTitle()', 'public Integer getTitle()')
                .replace('setTitle(String title)', 'setTitle(Integer title)')]).compile()

        then:
        !result.success
        result.graphqlErrors == ['BookFilter.java:11: [graphql] BookFilter.title: the argument or input field is mapped '
                                         + 'to the incorrect class: String accepts [java.lang.String], the Java type is '
                                         + 'java.lang.Integer. (schema.graphqls:22)']
    }

    void "an input type mapped to an interface fails"() {
        when:
        def result = compilation(['example.BookFilter': 'package example; public interface BookFilter { }']).compile()

        then:
        !result.success
        result.graphqlErrors == ['BookQuery.java:12: [graphql] Query.search(filter:): the argument or input field is '
                                         + 'mapped to the incorrect class: the input type BookFilter needs a custom class, '
                                         + 'the Java type is example.BookFilter. (schema.graphqls:7)']
    }

    void "a union mapped to a class instead of a Java interface fails"() {
        when:
        def result = compilation(['example.BookQuery': BOOK_QUERY.replace('public SearchResult result()', 'public Book result()')])
                .compile()

        then:
        !result.success
        result.graphqlErrors == ['BookQuery.java:16: [graphql] Query.result: the field is mapped to the incorrect class: '
                                         + 'the union SearchResult needs a Java interface, the Java type is example.Book. '
                                         + '(schema.graphqls:8)']
    }

    void "one object type reached through two Java classes fails"() {
        when:
        def result = compilation(['example.Novel'    : BOOK.replace('class Book', 'class Novel'),
                                  'example.BookQuery': BOOK_QUERY.replace('public List<Book> search', 'public List<Novel> search')])
                .compile()

        then:
        !result.success
        result.graphqlErrors == ['BookQuery.java:12: [graphql] Query.search: the object type Book is mapped to '
                                         + 'example.Novel here and to example.Book by Query.book. One schema type needs one '
                                         + 'Java type. (schema.graphqls:7)']
    }

    void "a model class without a bean introspection fails"() {
        when:
        def result = compilation(['example.Book': BOOK.replace('@io.micronaut.graphql.tools.annotation.GraphQLType\n', '')])
                .compile()

        then:
        !result.success
        result.graphqlErrors == ['Book.java:4: [graphql] Query.book: the object type Book is mapped to example.Book, '
                                         + 'which has no bean introspection. Annotate it with @GraphQLType. '
                                         + '(schema.graphqls:6)']
    }

    void "an incremental compilation checks an unchanged class of the module as the full compilation does; a class of a dependency is skipped"() {
        given:
        def plain = 'package example; public class Plain { public String getTitle() { return null; } }'
        def query = BOOK_QUERY.replace('public Book book', 'public Plain book')
                .replace('List<Book> search', 'List<Plain> search')
        def first = compilation(['example.Plain': plain]).compile()
        def dependency = compilation(['example.Plain': plain]).compile()

        when: 'only the query changed; Plain is read from the class output'
        def incremental = new Compilation().schema('schema.graphqls', SCHEMA).source('example.BookQuery', query)
                .incrementallyInto(first.classOutput).compile()
        def fromDependency = new Compilation().schema('schema.graphqls', SCHEMA).source('example.BookQuery', query)
                .classpath(dependency.classOutput).compile()

        then:
        first.success
        !incremental.success
        incremental.graphqlErrors == ['[graphql] Query.book: the object type Book is mapped to example.Plain, which has no '
                                              + 'bean introspection. Annotate it with @GraphQLType. (schema.graphqls:6)']
        fromDependency.success
    }

    void "a contract interface with its own introspection binds its getters only; an implementation must implement it"() {
        given:
        def contract = '''
package example;

@io.micronaut.graphql.tools.annotation.GraphQLType
public interface BookGraph extends SearchResult {
    String getTitle();

    Genre getGenre();
}
'''
        def implementation = { String implementsClause ->
            """
package example;

@io.micronaut.graphql.tools.annotation.GraphQLType(BookGraph.class)
public class Book ${implementsClause} {
    public String getTitle() {
        return null;
    }

    public Genre getGenre() {
        return null;
    }

    public String getIsbn() {
        return null;
    }
}
"""
        }
        def query = BOOK_QUERY.replace('public Book book', 'public BookGraph book')
                .replace('List<Book> search', 'List<BookGraph> search')

        when:
        def bound = compilation(['example.BookGraph': contract, 'example.Book': implementation('implements BookGraph'),
                                 'example.BookQuery': query]).compile()
        def implementationOnlyGetter = compilation(['example.BookGraph': contract,
                                                    'example.Book'     : implementation('implements BookGraph'),
                                                    'example.BookQuery': query],
                SCHEMA.replace('genre: Genre!', 'genre: Genre!\n  isbn: String')).compile()
        def notImplemented = compilation(['example.BookGraph': contract, 'example.Book': implementation(''),
                                          'example.BookQuery': query]).compile()

        then:
        bound.success
        !implementationOnlyGetter.success
        implementationOnlyGetter.graphqlErrors[0].contains('Book.isbn: nothing binds the field: example.BookGraph has no '
                + 'property or @GraphQLField method isbn')
        !notImplemented.success
        notImplemented.graphqlErrors == ['Book.java:5: [graphql] Query.book: example.Book is annotated '
                                                 + '@GraphQLType(BookGraph.class) but does not implement '
                                                 + 'example.BookGraph. (schema.graphqls:6)']
    }

    void "a Java interface without its own introspection is read through its single implementation: a strict-mode error; two implementations fail"() {
        given:
        def contract = 'package example; public interface BookGraph extends SearchResult { String getTitle(); Genre getGenre(); }'
        def implementation = { String name ->
            """
package example;

@io.micronaut.graphql.tools.annotation.GraphQLType(BookGraph.class)
public class ${name} implements BookGraph {
    public String getTitle() {
        return null;
    }

    public Genre getGenre() {
        return null;
    }
}
"""
        }
        def query = BOOK_QUERY.replace('public Book book', 'public BookGraph book')
                .replace('List<Book> search', 'List<BookGraph> search')

        when:
        def strict = compilation(['example.BookGraph': contract, 'example.Book': implementation('Book'),
                                  'example.BookQuery': query]).compile()
        def lenient = compilation(['example.BookGraph': contract, 'example.Book': implementation('Book'),
                                   'example.BookQuery': query]).option(ProcessorOptions.STRICT, 'false').compile()
        def two = compilation(['example.BookGraph': contract, 'example.Book': implementation('Book'),
                               'example.Novel'    : implementation('Novel'), 'example.BookQuery': query])
                .option(ProcessorOptions.STRICT, 'false').compile()

        then:
        !strict.success
        strict.graphqlErrors == ['BookGraph.java:1: [graphql] Query.book: the object type Book is mapped to the Java '
                                         + 'interface example.BookGraph, which has no introspection of its own, so it is '
                                         + 'read through the introspection of example.Book and a getter only the class '
                                         + 'declares binds silently (strict mode). Annotate the interface with '
                                         + '@GraphQLType or @Introspected. (schema.graphqls:6)']
        lenient.success
        !two.success
        two.graphqlErrors[0].contains('which has no introspection of its own and several classes annotated '
                + '@GraphQLType(BookGraph.class): example.Book, example.Novel.')
    }

    void "an object type mapped to a JDK interface or an interface of the compilation without implementation fails, as at startup"() {
        when:
        def list = compilation(['example.BookQuery': BOOK_QUERY.replace('public Book book(String id)',
                'public List<Book> book(String id)').replace('List<Book> search', 'List<String> search')],
                SCHEMA.replace('search(filter: BookFilter): [Book!]!', 'search(filter: BookFilter): [String!]!')).compile()
        def view = compilation(['example.BookView' : 'package example; public interface BookView { String getTitle(); }',
                                'example.BookQuery': BOOK_QUERY.replace('public Book book(String id)', 'public BookView book(String id)')
                                        .replace('List<Book> search', 'List<BookView> search')]).compile()

        then:
        !list.success
        list.graphqlErrors == ['BookQuery.java:8: [graphql] Query.book: the object type Book is mapped to the Java '
                                       + 'interface java.util.List, which has no introspection of its own and no class '
                                       + 'annotated @GraphQLType(List.class). Return a class with a bean introspection, or '
                                       + 'annotate the interface with @GraphQLType. (schema.graphqls:6)']
        !view.success
        view.graphqlErrors == ['BookQuery.java:8: [graphql] Query.book: the object type Book is mapped to the Java '
                                       + 'interface example.BookView, which has no introspection of its own and no class '
                                       + 'annotated @GraphQLType(BookView.class). Return a class with a bean '
                                       + 'introspection, or annotate the interface with @GraphQLType. (schema.graphqls:6)']
    }

    void "a getter a generic superclass declares is checked with the type argument the model gives it"() {
        given:
        def titled = '''
package example;

public abstract class Titled<T> {
    public T getTitle() {
        return null;
    }
}
'''
        def book = { String typeArgument ->
            BOOK.replace('public class Book implements SearchResult {',
                    "public class Book extends Titled<${typeArgument}> implements SearchResult {")
                    .replace('''    public String getTitle() {
        return null;
    }
''', '')
        }

        when:
        def matching = compilation(['example.Titled': titled, 'example.Book': book('String')]).compile()
        def mismatching = compilation(['example.Titled': titled, 'example.Book': book('Integer')]).compile()

        then:
        matching.success
        !mismatching.success
        mismatching.graphqlErrors.size() == 1
        mismatching.graphqlErrors[0].startsWith('Titled.java:5: [graphql] Book.title: the field is mapped to the incorrect '
                + 'class: String accepts [java.lang.String], the Java type is java.lang.Integer.')
    }

    void "@Introspected includes and excludes decide the properties, as in the introspection"() {
        when:
        def excluded = compilation(['example.Book': BOOK.replace('@io.micronaut.graphql.tools.annotation.GraphQLType\n',
                '@io.micronaut.graphql.tools.annotation.GraphQLType\n@io.micronaut.core.annotation.Introspected(excludes = "title")\n')])
                .compile()
        def included = compilation(['example.Book': BOOK.replace('@io.micronaut.graphql.tools.annotation.GraphQLType\n',
                '@io.micronaut.graphql.tools.annotation.GraphQLType\n@io.micronaut.core.annotation.Introspected(includes = "genre")\n')])
                .compile()

        then:
        !excluded.success
        excluded.graphqlErrors.size() == 1
        excluded.graphqlErrors[0].startsWith('Book.java:6: [graphql] Book.title: nothing binds the field: example.Book has no '
                + 'property or @GraphQLField method title')
        !included.success
        included.graphqlErrors.size() == 1
        included.graphqlErrors[0].startsWith('Book.java:6: [graphql] Book.title: nothing binds the field')
    }

    void "a model introspected with accessKind = FIELD is left to the startup, with a note, not rejected"() {
        when:
        def result = compilation(['example.Book': '''
package example;

@io.micronaut.graphql.tools.annotation.GraphQLType
@io.micronaut.core.annotation.Introspected(accessKind = io.micronaut.core.annotation.Introspected.AccessKind.FIELD)
public class Book implements SearchResult {
    public String title;
    public Genre genre;
}
''']).compile()

        then:
        result.success
        result.graphqlNotes.contains('[graphql] Book: not checked at compile time, the startup checks it: example.Book is '
                + 'introspected with accessKind = FIELD, which the processor does not model.')
    }

    void "a record model binds its components"() {
        when:
        def result = compilation(['example.Book': '''
package example;

@io.micronaut.graphql.tools.annotation.GraphQLType
public record Book(String title, Genre genre) implements SearchResult {
}
''']).compile()

        then:
        result.success
    }

    void "an enum argument and a list of input objects are checked through"() {
        given:
        def schema = SCHEMA.replace('search(filter: BookFilter)', 'search(filter: [BookFilter!])')
                .replace('book(id: ID!): Book', 'book(id: ID!, genre: Genre): Book')
        def query = BOOK_QUERY.replace('search(BookFilter filter)', 'search(List<BookFilter> filter)')

        when:
        def matching = compilation(['example.BookQuery': query.replace('book(String id)', 'book(String id, Genre genre)')],
                schema).compile()
        def wrongEnum = compilation(['example.BookQuery': query.replace('book(String id)', 'book(String id, String genre)')],
                schema).compile()

        then:
        matching.success
        !wrongEnum.success
        wrongEnum.graphqlErrors == ['BookQuery.java:8: [graphql] Query.book(genre:): the argument or input field is '
                                            + 'mapped to the incorrect class: the enum Genre needs a Java enum, the Java type '
                                            + 'is java.lang.String. (schema.graphqls:6)']
    }

}
