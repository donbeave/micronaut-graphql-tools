package io.micronaut.graphql.tools.mapping.resolver.coordinate

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.exceptions.InvalidSchemaCoordinateException
import io.micronaut.graphql.tools.exceptions.MethodNotFoundException
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory

/**
 * {@link GraphQLSchemaCoordinate} on resolver contract methods: only annotated methods of such a bean bind, and each
 * of them must end up bound to exactly the field its coordinate names.
 */
class SchemaCoordinateSpec extends AbstractTest {

    static final String SPEC_NAME = "SchemaCoordinateSpec"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  book(id: ID!): Book
}

type Book {
  id: ID!
  title: String!
  summary: String!
}
"""

    ListAppender<ILoggingEvent> appender

    void setup() {
        appender = new ListAppender<>()
        appender.start()
        generatorLogger().addAppender(appender)
    }

    void cleanup() {
        generatorLogger().detachAppender(appender)
    }

    void "methods of contracts with schema coordinates bind"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = executeQuery('{ book(id: "1") { id title summary } }')

        then:
        result.errors.isEmpty()
        result.data.book == [id: '1', title: 'Title 1', summary: 'Summary of Title 1']
    }

    void "a public root resolver method without a coordinate on a bean that uses coordinates does not bind"() {
        given:
        startContext("""
schema {
  query: Query
}

type Query {
  book(id: ID!): Book
  helper: String
}

type Book {
  id: ID!
  title: String!
  summary: String!
}
""", SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof MethodNotFoundException
        e.cause.message == """The method `helper` not found in any root resolvers: [${BookQuery.name}].
  GraphQL object type: Query
  GraphQL field: helper"""
    }

    void "a public type resolver method without a coordinate on a bean that uses coordinates does not bind"() {
        given:
        startContext("""
schema {
  query: Query
}

type Query {
  book(id: ID!): Book
}

type Book {
  id: ID!
  title: String!
  summary: String!
  blurb: String
}
""", SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof MethodNotFoundException
        e.cause.message == """The property or method `blurb` not found in ${Book.name}'s type or it resolvers: [${BookResolver.name}].
  GraphQL object type: Book
  GraphQL field: blurb"""
    }

    @GraphQLRootResolver
    static interface BookQueryContract {
        @GraphQLSchemaCoordinate("Query.book")
        Book book(String id)
    }

    @GraphQLTypeResolver(Book.class)
    static interface BookResolverContract {
        @GraphQLSchemaCoordinate("Book.summary")
        String summary(Book book)
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    static class BookQuery implements BookQueryContract {
        @Override
        Book book(String id) {
            return new Book(id: id, title: "Title " + id)
        }

        String helper() {
            return 'not a field'
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    static class BookResolver implements BookResolverContract {
        @Override
        String summary(Book book) {
            return "Summary of " + book.title
        }

        String blurb(Book book) {
            return 'not a field'
        }
    }

    @GraphQLType
    static class Book {
        String id
        String title
    }

    // --- the method must end up bound to exactly its coordinate ---

    void "a coordinate the binder does not bind the method to fails the startup (#name)"() {
        given:
        startContext(schema, SPEC_NAME + "." + name, config)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof InvalidSchemaCoordinateException
        e.cause.message == "Invalid @GraphQLSchemaCoordinate declarations:\n  1) " + problem

        where:
        name                 | schema                       | config                                                   | problem
        'typeMappedElsewhere' | AUTHOR_SCHEMA               | [:]                                                      | "@GraphQLSchemaCoordinate(\"Book.summary\") on ${AuthorResolver.name} summary(${Author.name} author): the method is not bound to Book.summary, Book.summary is bound to resolver ${PlainBookResolver.name} summary(${Book.name} book) (Book is mapped to ${Book.name}, the bean resolves ${Author.name})."
        // the strict default rejects a property and a type resolver for one field before the coordinate is checked
        'propertyBinds'      | SCHEMA                       | ['micronaut.graphql.tools.precedence': 'property-first'] | "@GraphQLSchemaCoordinate(\"Book.summary\") on ${ShadowedBookResolver.name} summary(${BookWithSummary.name} book): the method is not bound to Book.summary, Book.summary is bound to property ${BookWithSummary.name}.getSummary()."
    }

    @Language("GraphQL")
    static final String AUTHOR_SCHEMA = """
schema {
  query: Query
}

type Query {
  book(id: ID!): Book
  author: Author
}

type Book {
  id: ID!
  title: String!
  summary: String!
}

type Author {
  name: String!
}
"""

    @Requires(property = 'spec.name', value = SPEC_NAME + ".typeMappedElsewhere")
    @GraphQLRootResolver
    static class AuthorQuery {
        Book book(String id) {
            return new Book(id: id, title: "Title " + id)
        }

        Author author() {
            return new Author(name: 'Jane')
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".typeMappedElsewhere")
    @GraphQLTypeResolver(Book.class)
    static class PlainBookResolver {
        String summary(Book book) {
            return "Summary of " + book.title
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".typeMappedElsewhere")
    @GraphQLTypeResolver(Author.class)
    static class AuthorResolver {
        @GraphQLSchemaCoordinate("Book.summary")
        String summary(Author author) {
            return "Summary of " + author.name
        }
    }

    @GraphQLType
    static class Author {
        String name
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".propertyBinds")
    @GraphQLRootResolver
    static class ShadowedBookQuery {
        BookWithSummary book(String id) {
            return new BookWithSummary(id: id, title: "Title " + id, summary: 'stored summary')
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".propertyBinds")
    @GraphQLTypeResolver(BookWithSummary.class)
    static class ShadowedBookResolver {
        @GraphQLSchemaCoordinate("Book.summary")
        String summary(BookWithSummary book) {
            return "Summary of " + book.title
        }
    }

    @GraphQLType
    static class BookWithSummary {
        String id
        String title
        String summary
    }

    // --- a coordinate of a type left unbound on purpose ---

    void "a coordinate of an allowed unreachable type is not bound, and that is logged"() {
        given:
        startContext("""
schema {
  query: Query
}

type Query {
  book(id: ID!): Book
}

type Book {
  id: ID!
  title: String!
}

type Draft {
  note: String
}
""", SPEC_NAME + ".unreachable", ['micronaut.graphql.tools.allow-unreachable': ['Draft']])

        when:
        def result = executeQuery('{ book(id: "1") { id } }')

        then:
        result.errors.isEmpty()
        result.data.book == [id: '1']
        appender.list.any {
            it.level == Level.WARN && it.formattedMessage == "Methods whose @GraphQLSchemaCoordinate names an " +
                    "unreachable type are not bound: @GraphQLSchemaCoordinate(\"Draft.note\") on " +
                    "${DraftResolver.name} note(${Draft.name} draft)"
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".unreachable")
    @GraphQLRootResolver
    static class DraftQuery {
        Book book(String id) {
            return new Book(id: id, title: "Title " + id)
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".unreachable")
    @GraphQLTypeResolver(Draft.class)
    static class DraftResolver {
        @GraphQLSchemaCoordinate("Draft.note")
        String note(Draft draft) {
            return 'note'
        }
    }

    @GraphQLType
    static class Draft {
    }

    // --- several contracts on one bean: only coordinate methods have to be assignable to a contract ---

    void "a helper without a coordinate on a bean with several type resolver contracts is not a candidate"() {
        given:
        startContext("""
schema {
  query: Query
}

type Query {
  book(id: ID!): Book
}

type Book {
  id: ID!
  title: String!
  summary: String!
  author: Author
}

type Author {
  name: String!
  bio: String!
}
""", SPEC_NAME + ".severalContracts")

        when:
        def result = executeQuery('{ book(id: "1") { summary author { bio } } }')

        then:
        result.errors.isEmpty()
        result.data.book == [summary: 'Summary of Title 1', author: [bio: 'Bio of Jane']]
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".severalContracts")
    @GraphQLRootResolver
    static class SeveralContractsQuery {
        BookWithAuthor book(String id) {
            return new BookWithAuthor(id: id, title: "Title " + id, author: new Author(name: 'Jane'))
        }
    }

    @GraphQLType
    static class BookWithAuthor {
        String id
        String title
        Author author
    }

    @GraphQLTypeResolver(BookWithAuthor.class)
    static interface BookWithAuthorContract {
        @GraphQLSchemaCoordinate("Book.summary")
        String summary(BookWithAuthor book)
    }

    @GraphQLTypeResolver(Author.class)
    static interface AuthorContract {
        @GraphQLSchemaCoordinate("Author.bio")
        String bio(Author author)
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".severalContracts")
    @Singleton
    static class SeveralContractsResolver implements BookWithAuthorContract, AuthorContract {
        @Override
        String summary(BookWithAuthor book) {
            return "Summary of " + book.title
        }

        @Override
        String bio(Author author) {
            return "Bio of " + author.name
        }

        String format(String text) {
            return text
        }
    }

    private static Logger generatorLogger() {
        (Logger) LoggerFactory.getLogger("io.micronaut.graphql.tools.GraphQLRuntimeWiringGenerator")
    }

    // --- the coordinate is the binding key ---

    void "type resolver methods of one class bind the object type their coordinates name"() {
        given:
        startContext("""
schema {
  query: Query
}

type Query {
  book: Book
  novel: Novel
}

type Book {
  title: String!
  summary: String!
}

type Novel {
  title: String!
  summary: String!
}
""", SPEC_NAME + ".keyed")

        when:
        def result = executeQuery('{ book { summary } novel { summary } }')

        then:
        result.errors.isEmpty()
        result.data == [book: [summary: 'Book Dune'], novel: [summary: 'Novel Emma']]
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".keyed")
    @GraphQLRootResolver
    static class KeyedQuery {
        Book book() {
            return new Book(title: 'Dune')
        }

        Book novel() {
            return new Book(title: 'Emma')
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".keyed")
    @GraphQLTypeResolver(Book.class)
    static class BookSummaryResolver {
        @GraphQLSchemaCoordinate("Book.summary")
        String summary(Book book) {
            return 'Book ' + book.title
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".keyed")
    @GraphQLTypeResolver(Book.class)
    static class NovelSummaryResolver {
        @GraphQLSchemaCoordinate("Novel.summary")
        String summary(Book book) {
            return 'Novel ' + book.title
        }
    }

}
