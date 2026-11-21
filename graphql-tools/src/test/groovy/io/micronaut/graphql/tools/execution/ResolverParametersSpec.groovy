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
package io.micronaut.graphql.tools.execution

import graphql.ExecutionResult
import graphql.GraphQLContext
import graphql.schema.DataFetchingEnvironment
import graphql.schema.DataFetchingFieldSelectionSet
import io.micronaut.configuration.graphql.DefaultGraphQLInvocation
import io.micronaut.configuration.graphql.GraphQLInvocation
import io.micronaut.configuration.graphql.GraphQLInvocationData
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLContextValue
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.binding.BindingCode
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpResponse
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import spock.lang.Unroll

import java.security.Principal

/** The injectable parameters of a resolver method: one slot of the argument plan for each kind. */
class ResolverParametersSpec extends AbstractTest {

    static final String SPEC = 'ResolverParametersSpec'

    static final List<String> SELECTED = Collections.synchronizedList([])

    @Language('GraphQL')
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  context: String
  contextValue: String
  missingValue: String
  environment: String
  selected: Book
  locale: String
  request: String
  response: String
  optionalResponse: Boolean
  principal: String
  mixed(name: String!, count: Int!): String
  plain(name: String!): String
}

type Book {
  title: String
  author: String
}
"""

    @Language('GraphQL')
    static final String SCHEMA_ONE = """
schema {
  query: Query
}

type Query {
  hello(name: String!): String
}
"""

    void setup() {
        SELECTED.clear()
    }

    private ExecutionResult run(String query, HttpRequest request = null, MutableHttpResponse response = null) {
        Mono.from(applicationContext.getBean(GraphQLInvocation).invoke(new GraphQLInvocationData(query, null, [:]),
                request, response)).block()
    }

    private static Throwable causeOfType(Throwable e, Class<? extends Throwable> type) {
        for (Throwable cause = e; cause != null; cause = cause.cause) {
            if (type.isInstance(cause)) {
                return cause
            }
        }
        null
    }

    void "the GraphQLContext, a context value by key and by parameter name, and the environment inject"() {
        given:
        startContext(SCHEMA, SPEC + '.kinds')

        when:
        def result = run('{ context contextValue missingValue environment }')

        then:
        result.errors.isEmpty()
        result.data == [context: 'acme', contextValue: 'acme', missingValue: null, environment: 'Query.environment']
    }

    void "the selection set and the locale inject"() {
        given:
        startContext(SCHEMA, SPEC + '.kinds')

        when:
        def result = run('{ selected { title author } locale }')

        then:
        result.errors.isEmpty()
        SELECTED == ['title', 'author']
        result.data.locale == Locale.default.toLanguageTag()
    }

    void "the request, the response, an optional response and the principal inject"() {
        given:
        startContext(SCHEMA, SPEC + '.kinds')
        def request = HttpRequest.GET('/graphql')
        request.setUserPrincipal({ 'alice' } as Principal)
        def response = HttpResponse.ok()

        when:
        def result = run('{ request response optionalResponse principal }', request, response)

        then:
        result.errors.isEmpty()
        result.data == [request: '/graphql', response: 'true', optionalResponse: true, principal: 'alice']
        response.header('X-Resolver') == 'set'
    }

    void "without a request the request, the response and the principal are null and the optional response is empty"() {
        given:
        startContext(SCHEMA, SPEC + '.kinds')

        when:
        def result = run('{ request response optionalResponse principal }')

        then:
        result.errors.isEmpty()
        result.data == [request: null, response: 'false', optionalResponse: false, principal: null]
    }

    void "injected parameters bind by type in any position and the others still bind by name"() {
        given:
        startContext(SCHEMA, SPEC + '.kinds')

        when:
        def result = run('{ mixed(name: "n", count: 3) }')

        then:
        result.errors.isEmpty()
        result.data.mixed == 'n:3:acme:' + Locale.default.toLanguageTag() + ':Query.mixed'
    }

    void "a field without injected parameters reads no execution state and still works"() {
        given:
        startContext(SCHEMA, SPEC + '.kinds')

        when:
        def result = run('{ plain(name: "x") }')

        then:
        result.errors.isEmpty()
        result.data.plain == 'plain:x'
    }

    void "a context value of a primitive type fails the startup naming the method and the parameter"() {
        when:
        startContext(SCHEMA_ONE, SPEC + '.primitive', ['micronaut.graphql.tools.eager-init': true])

        then:
        def e = thrown(Exception)
        def cause = causeOfType(e, SchemaBindingException)
        cause.code() == BindingCode.ARGUMENT_MISMATCH
        cause.message.contains('Query.hello')
        cause.message.contains("'count'")
        cause.message.contains('primitive')
    }

    void "a parameter of an unknown kind fails the startup as an argument without a match"() {
        when:
        startContext(SCHEMA_ONE, SPEC + '.unknown', ['micronaut.graphql.tools.eager-init': true])

        then:
        def e = thrown(Exception)
        def cause = causeOfType(e, SchemaBindingException)
        cause.code() == BindingCode.ARGUMENT_MISMATCH
        cause.message.contains("'other'")
    }

    @Unroll
    void "a #kind parameter fails the startup with execution-context-unavailable when #form"() {
        when:
        startContext(SCHEMA_ONE, SPEC + '.' + form + '.' + kind, ['micronaut.graphql.tools.eager-init': true])

        then:
        def e = thrown(Exception)
        def cause = causeOfType(e, SchemaBindingException)
        cause.code() == BindingCode.EXECUTION_CONTEXT_UNAVAILABLE
        cause.message.contains(need)
        cause.message.contains('Query.hello')
        cause.message.contains(form == 'replaced' ? 'OwnInvocation' : 'PrimaryInvocation')

        where:
        kind        | form       | need
        'request'   | 'replaced' | 'the HTTP request'
        'request'   | 'primary'  | 'the HTTP request'
        'response'  | 'replaced' | 'the HTTP response'
        'response'  | 'primary'  | 'the HTTP response'
        'optional'  | 'replaced' | 'the HTTP response'
        'optional'  | 'primary'  | 'the HTTP response'
        'principal' | 'replaced' | 'the principal'
        'principal' | 'primary'  | 'the principal'
    }

    void "the parameters that need no execution state start although the application invocation replaced the library's"() {
        when:
        startContext(SCHEMA_ONE, SPEC + '.replaced.stateless', ['micronaut.graphql.tools.eager-init': true])

        then:
        noExceptionThrown()
    }

    @GraphQLType
    static class Book {
        String title
        String author
    }

    @Requires(property = 'spec.name', value = SPEC + '.kinds')
    @Singleton
    static class Contributor implements GraphQLContextContributor {
        @Override
        void contribute(GraphQLContext context, GraphQLExecutionRequest request) {
            context.put('tenant', 'acme')
        }
    }

    @Requires(property = 'spec.name', value = SPEC + '.kinds')
    @Singleton
    @GraphQLRootResolver
    static class Kinds {

        String context(GraphQLContext context) {
            context.get('tenant')
        }

        String contextValue(@GraphQLContextValue('tenant') String value) {
            value
        }

        String missingValue(@GraphQLContextValue String absent) {
            null
        }

        String environment(DataFetchingEnvironment environment) {
            environment.parentType.name + '.' + environment.fieldDefinition.name
        }

        Book selected(DataFetchingFieldSelectionSet selection) {
            SELECTED.addAll(selection.fields*.name)
            new Book(title: 't', author: 'a')
        }

        String locale(Locale locale) {
            locale.toLanguageTag()
        }

        String request(HttpRequest<?> request) {
            request?.path
        }

        String response(MutableHttpResponse<?> response) {
            response?.header('X-Resolver', 'set')
            String.valueOf(response != null)
        }

        boolean optionalResponse(Optional<MutableHttpResponse<?>> response) {
            response.isPresent()
        }

        String principal(Principal principal) {
            principal?.name
        }

        String mixed(Locale locale, String name, @GraphQLContextValue('tenant') String tenant, DataFetchingEnvironment env,
                     int count) {
            name + ':' + count + ':' + tenant + ':' + locale.toLanguageTag() + ':' + env.parentType.name + '.' + env.fieldDefinition.name
        }

        String plain(String name) {
            'plain:' + name
        }
    }

    @Requires(property = 'spec.name', value = SPEC + '.primitive')
    @Singleton
    @GraphQLRootResolver
    static class Primitive {
        String hello(String name, @GraphQLContextValue('tenant') int count) {
            name
        }
    }

    @Requires(property = 'spec.name', value = SPEC + '.unknown')
    @Singleton
    @GraphQLRootResolver
    static class Unknown {
        String hello(StringBuilder other) {
            null
        }
    }

    @Requires(property = 'spec.name', pattern = SPEC + '\\.(replaced|primary)\\.request')
    @Singleton
    @GraphQLRootResolver
    static class NeedsRequest {
        String hello(String name, HttpRequest<?> request) {
            name
        }
    }

    @Requires(property = 'spec.name', pattern = SPEC + '\\.(replaced|primary)\\.response')
    @Singleton
    @GraphQLRootResolver
    static class NeedsResponse {
        String hello(String name, MutableHttpResponse<?> response) {
            name
        }
    }

    @Requires(property = 'spec.name', pattern = SPEC + '\\.(replaced|primary)\\.optional')
    @Singleton
    @GraphQLRootResolver
    static class NeedsOptionalResponse {
        String hello(String name, Optional<MutableHttpResponse<?>> response) {
            name
        }
    }

    @Requires(property = 'spec.name', pattern = SPEC + '\\.(replaced|primary)\\.principal')
    @Singleton
    @GraphQLRootResolver
    static class NeedsPrincipal {
        String hello(String name, Principal principal) {
            name
        }
    }

    @Requires(property = 'spec.name', value = SPEC + '.replaced.stateless')
    @Singleton
    @GraphQLRootResolver
    static class Stateless {
        String hello(String name, GraphQLContext context, Locale locale, DataFetchingEnvironment env,
                     @GraphQLContextValue('tenant') String tenant) {
            name
        }
    }

    @Requires(property = 'spec.name', pattern = SPEC + '\\.replaced\\..*')
    @Singleton
    @Replaces(DefaultGraphQLInvocation)
    static class OwnInvocation implements GraphQLInvocation {
        @Override
        Publisher<ExecutionResult> invoke(GraphQLInvocationData invocationData, HttpRequest httpRequest, MutableHttpResponse<String> httpResponse) {
            Mono.empty()
        }
    }

    @Requires(property = 'spec.name', pattern = SPEC + '\\.primary\\..*')
    @Singleton
    @Primary
    static class PrimaryInvocation implements GraphQLInvocation {
        @Override
        Publisher<ExecutionResult> invoke(GraphQLInvocationData invocationData, HttpRequest httpRequest, MutableHttpResponse<String> httpResponse) {
            Mono.empty()
        }
    }

}
