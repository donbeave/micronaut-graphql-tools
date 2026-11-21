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
package io.micronaut.graphql.tools.security

import graphql.GraphQL
import graphql.execution.AbortExecutionException
import graphql.GraphQLContext
import graphql.language.OperationDefinition
import graphql.schema.FieldCoordinates
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.core.type.Argument
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.json.JsonMapper
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.filters.AuthenticationFetcher
import io.micronaut.security.rules.SecurityRule
import io.micronaut.security.utils.SecurityService
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language
import org.reactivestreams.Publisher
import spock.lang.Shared
import spock.lang.Specification

import java.net.URI
import java.net.http.WebSocket
import java.security.Principal
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * A real server with Micronaut Security and a real client: the principal after an async hop, secured and unsecured fields, the
 * hook that sees the root fields, and the authentication of a WebSocket upgrade request.
 */
class SecuritySpec extends Specification {

    static final String SPEC_NAME = 'SecuritySpec'

    static final List<Map> HOOKED = Collections.synchronizedList([])

    @Language('GraphQL')
    static final String SCHEMA = '''
schema { query: Query }

type Query {
  open: String
  secret: String
  admin: String
  nobody: String
  anonymousOk: String
  blocked: String
  me: Me
}

type Me {
  principal: String
  service: String
  roles: String
}
'''

    @Shared ApplicationContext context
    @Shared EmbeddedServer server
    @Shared HttpClient client
    @Shared JsonMapper json

    void setupSpec() {
        TypeDefinitionRegistry registry = new TypeDefinitionRegistry()
        registry.merge(new SchemaParser().parse(SCHEMA))
        context = ApplicationContext.builder(
                ['spec.name'                                     : SPEC_NAME,
                 'micronaut.server.host'                         : '127.0.0.1',
                 'micronaut.server.port'                         : -1,
                 'micronaut.security.enabled'                    : true,
                 'micronaut.security.intercept-url-map[0].pattern': '/**',
                 'micronaut.security.intercept-url-map[0].access[0]': SecurityRule.IS_ANONYMOUS,
                 'graphql.graphql-ws.enabled'                    : true,
                 'graphql.graphiql.enabled'                      : false], 'test')
                .singletons(registry)
                .start()
        server = context.getBean(EmbeddedServer).start()
        client = context.createBean(HttpClient, server.URL)
        json = context.getBean(JsonMapper)
    }

    void cleanupSpec() {
        client?.close()
        context?.close()
    }

    void setup() {
        HOOKED.clear()
    }

    private Map post(String query, Map<String, String> headers = [:]) {
        def request = HttpRequest.POST('/graphql', [query: query])
        headers.each { name, value -> request.header(name, value) }
        client.toBlocking().retrieve(request, Argument.mapOf(String, Object))
    }

    private static Map<String, String> as(String user, String roles = '') {
        ['X-User': user, 'X-Roles': roles]
    }

    void "the principal is available in a resolver and after an async hop, as a parameter and through SecurityService"() {
        when:
        def response = post('{ me { principal service roles } }', as('alice', 'ROLE_USER,ROLE_X'))

        then:
        response.errors == null
        response.data.me == [principal: 'alice', service: 'alice', roles: 'ROLE_USER,ROLE_X']
    }

    void "the principal of one request does not leak into another"() {
        when:
        def first = post('{ me { principal } }', as('alice'))
        def second = post('{ me { principal } }', as('bob'))
        def anonymous = post('{ me { principal service } }')

        then:
        first.data.me.principal == 'alice'
        second.data.me.principal == 'bob'
        anonymous.data.me == [principal: null, service: null]
    }

    void "a secured field rejects an anonymous call with a GraphQL error and does not run the resolver"() {
        when:
        def response = post('{ open secret admin nobody }')

        then:
        response.data.open == 'open'
        response.data.secret == null
        response.data.admin == null
        response.data.nobody == null
        response.errors*.path == [['secret'], ['admin'], ['nobody']]
        response.errors*.extensions.code == ['UNAUTHENTICATED', 'UNAUTHENTICATED', 'UNAUTHENTICATED']
        response.errors[0].message == 'Authentication is required to call Query.secret'
    }

    void "an authenticated call passes IS_AUTHENTICATED, needs a role for a role field and never passes DENY_ALL"() {
        when:
        def user = post('{ open secret admin nobody }', as('alice', 'ROLE_USER'))
        def admin = post('{ secret admin nobody }', as('root', 'ROLE_ADMIN'))

        then:
        user.data.open == 'open'
        user.data.secret == 'secret'
        user.data.admin == null
        user.errors*.path == [['admin'], ['nobody']]
        user.errors*.extensions.code == ['FORBIDDEN', 'FORBIDDEN']
        user.errors[0].message == 'Access to Query.admin is forbidden'
        admin.data.secret == 'secret'
        admin.data.admin == 'admin'
        admin.errors*.path == [['nobody']]
    }

    void "a field that allows anonymous callers and an unsecured field keep the base fetcher, a secured field has the wrapper"() {
        given:
        def graphQL = context.getBean(GraphQL)
        def fetcherOf = { String type, String field ->
            def definition = graphQL.graphQLSchema.getObjectType(type).getFieldDefinition(field)
            graphQL.graphQLSchema.codeRegistry.getDataFetcher(FieldCoordinates.coordinates(type, field), definition).class.simpleName
        }

        expect:
        fetcherOf('Query', 'open') != 'SecuredDataFetcher'
        fetcherOf('Query', 'anonymousOk') != 'SecuredDataFetcher'
        fetcherOf('Me', 'principal') != 'SecuredDataFetcher'
        fetcherOf('Query', 'secret') == 'SecuredDataFetcher'
        fetcherOf('Query', 'admin') == 'SecuredDataFetcher'
        fetcherOf('Query', 'nobody') == 'SecuredDataFetcher'
    }

    void "the hook receives the root field names, through fragments, without duplicates and without aliases"() {
        when:
        post('query Op { first: open ...Roots secret second: open } fragment Roots on Query { admin ... on Query { anonymousOk } }',
                as('root', 'ROLE_ADMIN'))

        then:
        HOOKED.size() == 1
        HOOKED[0].operation == OperationDefinition.Operation.QUERY
        HOOKED[0].fields == ['open', 'admin', 'anonymousOk', 'secret']
        HOOKED[0].authenticated
    }

    void "a hook that throws AbortExecutionException rejects the operation before any field is fetched"() {
        when:
        def response = post('{ open blocked }')

        then:
        response.errors*.message == ['Root field blocked is not allowed']
        response.data == null
    }

    void "the upgrade request of a WebSocket connection authenticates the operations of the connection"() {
        when:
        def alice = websocketData(as('alice', 'ROLE_USER'), '{ secret me { principal service } }')
        def anonymous = websocketData([:], '{ open secret }')

        then:
        alice.payload.data.secret == 'secret'
        alice.payload.data.me == [principal: 'alice', service: 'alice']
        alice.payload.errors == null
        anonymous.payload.data.open == 'open'
        anonymous.payload.data.secret == null
        anonymous.payload.errors*.extensions.code == ['UNAUTHENTICATED']
    }

    private Map websocketData(Map<String, String> headers, String query) {
        BlockingQueue<String> received = new LinkedBlockingQueue<>()
        def listener = new WebSocket.Listener() {
            private final StringBuilder buffer = new StringBuilder()

            CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                buffer.append(data)
                if (last) {
                    received.add(buffer.toString())
                    buffer.setLength(0)
                }
                webSocket.request(1)
                null
            }
        }
        def http = java.net.http.HttpClient.newHttpClient()
        def builder = http.newWebSocketBuilder().subprotocols('graphql-transport-ws')
        headers.each { name, value -> builder.header(name, value) }
        WebSocket socket = builder.buildAsync(URI.create("ws://${server.host}:${server.port}/graphql-ws"), listener).get(10, TimeUnit.SECONDS)
        try {
            socket.sendText(json.writeValueAsString([type: 'connection_init']), true).get(10, TimeUnit.SECONDS)
            assert json.readValue(received.poll(10, TimeUnit.SECONDS), Map).type == 'connection_ack'
            socket.sendText(json.writeValueAsString([id: '1', type: 'subscribe', payload: [query: query]]), true).get(10, TimeUnit.SECONDS)
            return json.readValue(received.poll(10, TimeUnit.SECONDS), Map)
        } finally {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, 'done')
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = SPEC_NAME)
    static class HeaderAuthenticationFetcher implements AuthenticationFetcher<HttpRequest<?>> {

        @Override
        Publisher<Authentication> fetchAuthentication(HttpRequest<?> request) {
            String user = request.headers.get('X-User')
            if (user == null) {
                return Publishers.empty()
            }
            String roles = request.headers.get('X-Roles')
            Publishers.just(Authentication.build(user, roles ? roles.split(',').toList() : []))
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = SPEC_NAME)
    static class Hook implements RootFieldsHook {

        @Override
        void onRootFields(OperationDefinition.Operation operation, List<String> rootFields, GraphQLContext context) {
            HOOKED << [operation: operation, fields: rootFields, authenticated: context.get(GraphQLSecurity.AUTHENTICATION_KEY) != null]
            if (rootFields.contains('blocked')) {
                throw new AbortExecutionException('Root field blocked is not allowed')
            }
        }
    }

    @GraphQLType
    static class Me {
    }

    @Singleton
    @GraphQLRootResolver
    @Requires(property = 'spec.name', value = SPEC_NAME)
    static class Queries {

        String open() {
            'open'
        }

        @Secured(SecurityRule.IS_AUTHENTICATED)
        String secret() {
            'secret'
        }

        @Secured('ROLE_ADMIN')
        String admin() {
            'admin'
        }

        @Secured(SecurityRule.DENY_ALL)
        String nobody() {
            'nobody'
        }

        @Secured(SecurityRule.IS_ANONYMOUS)
        String anonymousOk() {
            'anonymous'
        }

        String blocked() {
            'blocked'
        }

        CompletionStage<Me> me() {
            CompletableFuture.supplyAsync({ new Me() })
        }
    }

    @Singleton
    @GraphQLTypeResolver(Me)
    @Requires(property = 'spec.name', value = SPEC_NAME)
    static class MeFields {

        private final SecurityService securityService

        MeFields(SecurityService securityService) {
            this.securityService = securityService
        }

        CompletionStage<String> principal(Me me, Principal principal) {
            CompletableFuture.supplyAsync({ principal?.name })
        }

        String service(Me me) {
            securityService.getAuthentication().map { it.name }.orElse(null)
        }

        String roles(Me me, Authentication authentication) {
            authentication?.roles?.join(',')
        }
    }

}
