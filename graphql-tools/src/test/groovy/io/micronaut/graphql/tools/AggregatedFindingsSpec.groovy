package io.micronaut.graphql.tools

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.exceptions.BindingFailuresException
import io.micronaut.graphql.tools.exceptions.InvalidSchemaCoordinateException
import io.micronaut.graphql.tools.exceptions.MethodNotFoundException
import io.micronaut.graphql.tools.exceptions.StrictModeException
import io.micronaut.graphql.tools.exceptions.UnreachableTypesException
import org.intellij.lang.annotations.Language

class AggregatedFindingsSpec extends AbstractTest {

    static final String SPEC_NAME = "AggregatedFindingsSpec"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  book: Book
  author: Author
}

type Book {
  title: String!
  summary: String!
}

type Author {
  name: String!
}

type Draft {
  note: String
}
"""

    void "an unreachable type, a stale coordinate and an unbound public method fail once, listing all three"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof BindingFailuresException

        BindingFailuresException failures = (BindingFailuresException) e.cause
        failures.failures*.class == [UnreachableTypesException, InvalidSchemaCoordinateException, StrictModeException]
        failures.message.startsWith("[binding-failures] The schema cannot be bound: 3 errors.\n  1) [unreachable-type] ")
        failures.message.contains("\n  2) [unknown-schema-coordinate] Invalid @GraphQLSchemaCoordinate declarations:")
        failures.message.contains("\n  3) [strict-findings] Strict mode found problems in the binding of the schema:")
        failures.message.contains("Draft (type)")
        failures.message.contains("the method is not bound to Book.summary")
        failures.message.contains("${Query.name}#helper(java.lang.String) binds no schema field")
    }

    void "with strict false the strict findings are left out and the others are still reported together"() {
        given:
        startContext(SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.strict': false])

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof BindingFailuresException
        e.cause.failures*.class == [UnreachableTypesException, InvalidSchemaCoordinateException]
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        Book book() {
            return new Book(title: 'Dune')
        }

        Author author() {
            return new Author(name: 'Jane')
        }

        String helper(String value) {
            return value
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLTypeResolver(Book.class)
    static class BookResolver {
        String summary(Book book) {
            return "Summary of " + book.title
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLTypeResolver(Author.class)
    static class AuthorResolver {
        @GraphQLSchemaCoordinate("Book.summary")
        String summary(Author author) {
            return "Summary of " + author.name
        }
    }

    @GraphQLType
    static class Book {
        String title
    }

    @GraphQLType
    static class Author {
        String name
    }

    @Language("GraphQL")
    static final String SCALAR_SCHEMA = """
schema {
  query: Query
}

scalar Stamp

union Mixed = Stamp

type Query {
  a: String
  b: Mixed
}
"""

    void "a missing method and an unsupported union member are both reported, each with its coordinate"() {
        given:
        startContext(SCALAR_SCHEMA, SPEC_NAME + ".scalar")

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof BindingFailuresException

        BindingFailuresException failures = (BindingFailuresException) e.cause
        failures.failures*.mappingContext*.coordinate == ['Query.a', 'Query.b']
        failures.failures[0] instanceof MethodNotFoundException
        failures.failures[1].message.contains('Unsupported type definition')
        failures.failures[1].message.contains('GraphQL coordinate: Query.b')
        failures.failures[1].message.count('GraphQL coordinate:') == 1
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".scalar")
    @GraphQLRootResolver
    static class ScalarQuery {
        Mixed b() {
            return null
        }
    }

    static interface Mixed {
    }

}
