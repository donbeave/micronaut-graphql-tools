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
package io.micronaut.graphql.tools.mapping.input

import jakarta.inject.Singleton
import graphql.schema.DataFetchingEnvironment
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLArgument
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import org.intellij.lang.annotations.Language
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.graphql.tools.binding.BindingCode

class ArgumentBindingSpec extends AbstractTest {

    static final String SPEC_REORDERED = "ArgumentBindingSpecReordered"
    static final String SPEC_SDL_REORDERED = "ArgumentBindingSpecSdlReordered"
    static final String SPEC_RENAMED = "ArgumentBindingSpecRenamed"
    static final String SPEC_JAVA = "ArgumentBindingSpecJava"
    static final String SPEC_MISNAMED = "ArgumentBindingSpecMisnamed"
    static final String SPEC_DUPLICATE = "ArgumentBindingSpecDuplicate"
    static final String SPEC_EXTRA = "ArgumentBindingSpecExtra"
    static final String SPEC_MISSING = "ArgumentBindingSpecMissing"
    static final String SPEC_SOURCE = "ArgumentBindingSpecSource"
    static final String SPEC_BAD_OVERRIDE = "ArgumentBindingSpecBadOverride"
    static final String SPEC_CONTRACT = "ArgumentBindingSpecContract"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  page(first: Int!, offset: Int!): String!
  renamed(long: Int!, short: Int!): String!
}
"""

    @Language("GraphQL")
    static final String SCHEMA_SDL_REORDERED = """
schema {
  query: Query
}

type Query {
  page(offset: Int!, first: Int!): String!
}
"""

    @Language("GraphQL")
    static final String SCHEMA_PAGE = """
schema {
  query: Query
}

type Query {
  page(first: Int!, offset: Int!): String!
}
"""

    void "parameters declared in another order than the SDL arguments are bound by name"() {
        given:
        startContext(SCHEMA, SPEC_REORDERED)

        expect:
        page() == 'first=10 offset=200'
    }

    void "the same bindings for a Java resolver"() {
        given:
        startContext(SCHEMA, SPEC_JAVA)

        expect:
        page() == 'first=10 offset=200'
        executeQuery('{ renamed(long: 7, short: 3) }').data.renamed == 'long=7 short=3'
    }

    void "reordering two same-typed SDL arguments keeps the responses unchanged"() {
        given:
        startContext(SCHEMA_SDL_REORDERED, SPEC_SDL_REORDERED)

        expect:
        page() == 'first=10 offset=200'
    }

    void "@GraphQLArgument binds a parameter whose name cannot match"() {
        given:
        startContext(SCHEMA, SPEC_RENAMED)

        expect:
        executeQuery('{ renamed(long: 7, short: 3) }').data.renamed == 'long=7 short=3'
    }

    void "a source argument and a trailing DataFetchingEnvironment keep their places"() {
        given:
        startContext("""
schema { query: Query }
type Query { user: User! }
type User { name(prefix: String!, suffix: String!): String! }
""", SPEC_SOURCE)

        expect:
        executeQuery('{ user { name(prefix: "a", suffix: "b") } }').data.user.name == 'user:a-b'
    }

    void "a misnamed parameter fails the startup and names the coordinate and the parameter"() {
        given:
        startContext(SCHEMA_PAGE, SPEC_MISNAMED)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.getCode() == BindingCode.ARGUMENT_MISMATCH
        e.cause.message.contains("The method parameter 'count' has no GraphQL argument with that name " +
                "(arguments: [first, offset])")
        e.cause.message.contains('@GraphQLArgument("<argument name>")')
        e.cause.message.contains('GraphQL coordinate: Query.page')
        e.cause.mappingContext.graphQlField == 'page'
    }

    void "two parameters bound to one argument fail the startup"() {
        given:
        startContext(SCHEMA_PAGE, SPEC_DUPLICATE)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.getCode() == BindingCode.ARGUMENT_MISMATCH
        e.cause.message.contains("More than one method parameter is bound to the GraphQL argument 'first'")
        e.cause.message.contains('GraphQL coordinate: Query.page')
    }

    void "an extra parameter fails the startup"() {
        given:
        startContext(SCHEMA_PAGE, SPEC_EXTRA)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.getCode() == BindingCode.ARGUMENT_MISMATCH
        e.cause.message.contains('too many arguments')
        e.cause.message.contains('page(Int first, Int offset)')
    }

    void "a missing parameter fails the startup"() {
        given:
        startContext(SCHEMA_PAGE, SPEC_MISSING)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.getCode() == BindingCode.ARGUMENT_MISMATCH
        e.cause.message.contains('too few arguments')
        e.cause.message.contains('GraphQL coordinate: Query.page')
    }

    void "a mistyped @GraphQLArgument value names the Java parameter and the requested argument"() {
        given:
        startContext(SCHEMA_PAGE, SPEC_BAD_OVERRIDE)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.getCode() == BindingCode.ARGUMENT_MISMATCH
        e.cause.message.contains("The method parameter 'first' is bound by @GraphQLArgument(\"frist\")")
        e.cause.message.contains('(arguments: [first, offset])')
        e.cause.message.contains('GraphQL coordinate: Query.page')
    }

    void "@GraphQLArgument declared on a resolver contract applies to its implementation"() {
        given:
        startContext(SCHEMA, SPEC_CONTRACT)

        expect:
        executeQuery('{ renamed(long: 7, short: 3) }').data.renamed == 'long=7 short=3'
    }

    private String page() {
        def result = executeQuery('{ page(first: 10, offset: 200) }')
        assert result.errors.isEmpty()
        return result.data.page
    }

    @Requires(property = 'spec.name', value = SPEC_REORDERED)
    @Singleton
    @GraphQLRootResolver
    static class ReorderedQuery {
        String page(Integer offset, Integer first) { "first=$first offset=$offset" }
        String renamed(@GraphQLArgument('long') Integer length, @GraphQLArgument('short') Integer width) { null }
    }

    @Requires(property = 'spec.name', value = SPEC_SDL_REORDERED)
    @Singleton
    @GraphQLRootResolver
    static class SdlReorderedQuery {
        String page(Integer offset, Integer first) { "first=$first offset=$offset" }
    }

    @Requires(property = 'spec.name', value = SPEC_RENAMED)
    @Singleton
    @GraphQLRootResolver
    static class RenamedQuery {
        String page(Integer first, Integer offset) { null }
        String renamed(@GraphQLArgument('long') Integer length, @GraphQLArgument('short') Integer width) {
            "long=$length short=$width"
        }
    }

    @Requires(property = 'spec.name', value = SPEC_MISNAMED)
    @Singleton
    @GraphQLRootResolver
    static class MisnamedQuery {
        String page(Integer count, Integer offset) { null }
    }

    @Requires(property = 'spec.name', value = SPEC_DUPLICATE)
    @Singleton
    @GraphQLRootResolver
    static class DuplicateQuery {
        String page(Integer first, @GraphQLArgument('first') Integer offset) { null }
    }

    @Requires(property = 'spec.name', value = SPEC_EXTRA)
    @Singleton
    @GraphQLRootResolver
    static class ExtraQuery {
        String page(Integer first, Integer offset, Integer limit) { null }
    }

    @Requires(property = 'spec.name', value = SPEC_MISSING)
    @Singleton
    @GraphQLRootResolver
    static class MissingQuery {
        String page(Integer first) { null }
    }

    @Requires(property = 'spec.name', value = SPEC_SOURCE)
    @Singleton
    @GraphQLRootResolver
    static class SourceQuery {
        User user() { new User() }
    }

    @GraphQLType
    static class User {
    }

    @Requires(property = 'spec.name', value = SPEC_SOURCE)
    @Singleton
    @GraphQLTypeResolver(User)
    static class UserResolver {
        String name(User user, String suffix, String prefix, DataFetchingEnvironment env) {
            "user:$prefix-$suffix"
        }
    }

    @Requires(property = 'spec.name', value = SPEC_BAD_OVERRIDE)
    @Singleton
    @GraphQLRootResolver
    static class BadOverrideQuery {
        String page(@GraphQLArgument('frist') Integer first, Integer offset) { null }
    }

    interface RenamedContract {
        String page(Integer first, Integer offset)
        String renamed(@GraphQLArgument('long') Integer length, @GraphQLArgument('short') Integer width)
    }

    @Requires(property = 'spec.name', value = SPEC_CONTRACT)
    @Singleton
    @GraphQLRootResolver
    static class ContractQuery implements RenamedContract {
        @Override
        String page(Integer first, Integer offset) { null }

        @Override
        String renamed(Integer length, Integer width) { "long=$length short=$width" }
    }

}
