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
package io.micronaut.graphql.tools.mapping.resolver.root

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import org.intellij.lang.annotations.Language
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.graphql.tools.binding.BindingCode

class RootResolverMultipleImplementationsFoundSpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverMultipleImplementationsSpec"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  user: User
}

type User {
  username: String
}
"""

    void "method returns interfaces which has multiple GraphQLType implementations"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.code() == BindingCode.MULTIPLE_IMPLEMENTATIONS_FOUND
        e.cause.message == """[multiple-implementations-found] Found multiple implementations for the interface ${User.name}. Annotate the interface with @GraphQLType, so that its own bean introspection reads every implementation.
  GraphQL coordinate: Query.user (line 7, column 3)
  Mapped class: ${Query.name}
  Mapped method: user()
  Implementation classes: ${UserAltImpl.name}, ${UserImpl.name}"""
        e.cause.mappingContext.graphQlObjectType == 'Query'
        e.cause.mappingContext.graphQlField == 'user'
        e.cause.mappingContext.mappedClass == Query
        e.cause.mappingContext.mappedMethod == "user()"
        e.cause.implementationClasses == [UserAltImpl, UserImpl]
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        User user() {
            return null
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    static interface User {
    }

    @GraphQLType(User)
    static class UserImpl implements User {
    }

    @GraphQLType(User)
    static class UserAltImpl implements User {
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLTypeResolver(User.class)
    static class UserResolver {
        String username(User user) {
            return null
        }
    }

}
