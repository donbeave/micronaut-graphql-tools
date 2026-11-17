package io.micronaut.graphql.tools.mapping.resolver.inherited

import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.exceptions.MultipleMethodsFoundException
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language

/**
 * The documented fix for a resolver subclass next to its original (resolver stereotypes are inherited):
 * {@code @Replaces(<original>.class)} on the subclass drops the original from the resolver collection, whereas
 * {@code @Requires(env = "test")} alone does not, because both beans are active in that environment.
 */
class ReplacesResolverSpec extends AbstractTest {

    static final String SPEC_NAME = "ReplacesResolverSpec"

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
  summary: String!
}
"""

    @Language("GraphQL")
    static final String ROOT_SCHEMA = """
schema {
  query: Query
}

type Query {
  book(id: ID!): Book
}

type Book {
  id: ID!
}
"""

    void "a @Replaces subclass of a root resolver binds, only the replacement answers"() {
        given:
        startContext(ROOT_SCHEMA, SPEC_NAME + ".root")

        when:
        def result = executeQuery('{ book(id: "1") { id } }')

        then:
        result.errors.isEmpty()
        result.data.book == [id: '1']
        applicationContext.getBeansOfType(OriginalQuery).collect { it.getClass() } == [ReplacementQuery]
    }

    void "a @Replaces subclass of a type resolver binds, only the replacement answers"() {
        given:
        startContext(SCHEMA, SPEC_NAME + ".type")

        when:
        def result = executeQuery('{ book(id: "1") { summary } }')

        then:
        result.errors.isEmpty()
        result.data.book == [summary: 'replacement summary']
        applicationContext.getBeansOfType(OriginalBookResolver).collect { it.getClass() } == [ReplacementBookResolver]
    }

    void "@Requires(env = 'test') alone does not drop the original: both beans bind the field and the message names the fix"() {
        given:
        startContext(ROOT_SCHEMA, SPEC_NAME + ".requiresOnly")

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof MultipleMethodsFoundException
        e.cause.message.contains("GraphQL coordinate: Query.book")
        e.cause.message.contains("${OriginalQuery.name} book(java.lang.String id)")
        e.cause.message.contains("${TestOnlyQuery.name} book(java.lang.String id)")
        e.cause.message.contains("@Replaces(<overridden bean>.class)")
        e.cause.message.contains('@GraphQLSchemaCoordinate("Query.book")')
    }

    @Requires(property = 'spec.name', pattern = SPEC_NAME + '\\.(root|requiresOnly)')
    @GraphQLRootResolver
    static class OriginalQuery {
        Book book(String id) {
            return new Book(id: id)
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".root")
    @Replaces(OriginalQuery)
    @Singleton
    static class ReplacementQuery extends OriginalQuery {
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".requiresOnly")
    @Requires(env = "test")
    @Singleton
    static class TestOnlyQuery extends OriginalQuery {
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".type")
    @GraphQLRootResolver
    static class TypeQuery {
        Book book(String id) {
            return new Book(id: id)
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".type")
    @GraphQLTypeResolver(Book)
    static class OriginalBookResolver {
        String summary(Book book) {
            return "original summary"
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".type")
    @Replaces(OriginalBookResolver)
    @Singleton
    static class ReplacementBookResolver extends OriginalBookResolver {
        @Override
        String summary(Book book) {
            return "replacement summary"
        }
    }

    @GraphQLType
    static class Book {
        String id
    }

}
