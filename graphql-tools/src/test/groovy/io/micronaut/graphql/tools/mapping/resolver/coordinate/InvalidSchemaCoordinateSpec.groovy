package io.micronaut.graphql.tools.mapping.resolver.coordinate

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.exceptions.InvalidSchemaCoordinateException
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language

/**
 * A {@link GraphQLSchemaCoordinate} that does not match the schema, its method or its bean fails the startup before
 * anything is bound; all such coordinates are reported in one error.
 */
class InvalidSchemaCoordinateSpec extends AbstractTest {

    static final String SPEC_NAME = "InvalidSchemaCoordinateSpec"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
  mutation: Mutation
}

type Query {
  book(id: ID!): Book
}

type Mutation {
  rename(id: ID!, title: String!): Book
}

type Book {
  id: ID!
  title: String!
  summary: String!
}

enum Genre {
  NOVEL
}
"""

    void "an invalid coordinate fails the startup (#name)"() {
        given:
        startContext(SCHEMA, SPEC_NAME + "." + name)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof InvalidSchemaCoordinateException
        e.cause.message == "Invalid @GraphQLSchemaCoordinate declarations:\n  1) " + problem
        e.cause.problems == [problem]

        where:
        name               | problem
        'unknownType'      | "@GraphQLSchemaCoordinate(\"Novel.summary\") on ${UnknownTypeResolver.name} summary(${Book.name} book): the schema has no type Novel."
        'notObjectType'    | "@GraphQLSchemaCoordinate(\"Genre.summary\") on ${EnumTypeResolver.name} summary(${Book.name} book): Genre is not an object type."
        'unknownField'     | "@GraphQLSchemaCoordinate(\"Book.synopsis\") on ${UnknownFieldResolver.name} synopsis(${Book.name} book): the type Book has no field synopsis."
        'nameMismatch'     | "@GraphQLSchemaCoordinate(\"Book.summary\") on ${NameMismatchResolver.name} summaryText(${Book.name} book): the field name summary differs from the method name summaryText."
        'malformed'        | "@GraphQLSchemaCoordinate(\"summary\") on ${MalformedResolver.name} summary(${Book.name} book): the value is not a schema coordinate of the form Type.field."
        'rootOnObjectType' | "@GraphQLSchemaCoordinate(\"Book.title\") on ${RootOnObjectTypeQuery.name} title(): a @GraphQLRootResolver binds fields of the root operation types [Mutation, Query] only, not of Book."
        'typeOnRootType'   | "@GraphQLSchemaCoordinate(\"Query.book\") on ${TypeOnRootTypeResolver.name} book(${Book.name} book): a @GraphQLTypeResolver cannot bind a field of the root operation type Query."
        'notAResolver'     | "@GraphQLSchemaCoordinate(\"Book.summary\") on ${NotAResolver.name} summary(${Book.name} book): the bean is neither a @GraphQLRootResolver nor a @GraphQLTypeResolver."
    }

    void "all invalid coordinates are reported in one error"() {
        given:
        startContext(SCHEMA, SPEC_NAME + ".several")

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof InvalidSchemaCoordinateException
        e.cause.message == """Invalid @GraphQLSchemaCoordinate declarations:
  1) @GraphQLSchemaCoordinate("Book.summary") on ${SeveralResolver.name} summaryText(${Book.name} book): the field name summary differs from the method name summaryText.
  2) @GraphQLSchemaCoordinate("Book.synopsis") on ${SeveralResolver.name} synopsis(${Book.name} book): the type Book has no field synopsis."""
    }

    @Requires(property = 'spec.name', pattern = SPEC_NAME + '\\..*')
    @GraphQLRootResolver
    static class Query {
        Book book(String id) {
            return new Book(id: id, title: "Title " + id)
        }

        Book rename(String id, String title) {
            return new Book(id: id, title: title)
        }
    }

    @GraphQLType
    static class Book {
        String id
        String title
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".unknownType")
    @GraphQLTypeResolver(Book.class)
    static class UnknownTypeResolver {
        @GraphQLSchemaCoordinate("Novel.summary")
        String summary(Book book) {
            return 'summary'
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".notObjectType")
    @GraphQLTypeResolver(Book.class)
    static class EnumTypeResolver {
        @GraphQLSchemaCoordinate("Genre.summary")
        String summary(Book book) {
            return 'summary'
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".unknownField")
    @GraphQLTypeResolver(Book.class)
    static class UnknownFieldResolver {
        @GraphQLSchemaCoordinate("Book.synopsis")
        String synopsis(Book book) {
            return 'synopsis'
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".nameMismatch")
    @GraphQLTypeResolver(Book.class)
    static class NameMismatchResolver {
        @GraphQLSchemaCoordinate("Book.summary")
        String summaryText(Book book) {
            return 'summary'
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".malformed")
    @GraphQLTypeResolver(Book.class)
    static class MalformedResolver {
        @GraphQLSchemaCoordinate("summary")
        String summary(Book book) {
            return 'summary'
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".rootOnObjectType")
    @GraphQLRootResolver
    static class RootOnObjectTypeQuery {
        @GraphQLSchemaCoordinate("Book.title")
        String title() {
            return 'title'
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".typeOnRootType")
    @GraphQLTypeResolver(Book.class)
    static class TypeOnRootTypeResolver {
        @GraphQLSchemaCoordinate("Query.book")
        Book book(Book book) {
            return book
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".notAResolver")
    @Singleton
    static class NotAResolver {
        @GraphQLSchemaCoordinate("Book.summary")
        String summary(Book book) {
            return 'summary'
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".several")
    @GraphQLTypeResolver(Book.class)
    static class SeveralResolver {
        @GraphQLSchemaCoordinate("Book.synopsis")
        String synopsis(Book book) {
            return 'synopsis'
        }

        @GraphQLSchemaCoordinate("Book.summary")
        String summaryText(Book book) {
            return 'summary'
        }
    }

}
