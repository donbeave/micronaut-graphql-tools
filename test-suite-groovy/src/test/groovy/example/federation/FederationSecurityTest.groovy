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
package example.federation

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

// The Groovy suite runs on the JVM: the callers of a subgraph over HTTP, with Micronaut Security and the security module.
class FederationSecurityTest extends Specification {

    private static final String SERVICE = '{ _service { sdl } }'

    private static final String PRODUCT = '{ _entities(representations: [{__typename: "Product", id: "p1"}]) { __typename ... on Product { id name } } }'

    private static final String ORDER = '{ _entities(representations: [{__typename: "Order", id: "o1"}]) { __typename ... on Order { id total } } }'

    private static final Map<String, String> ANONYMOUS = [:]

    private static final Map<String, String> USER = ['X-User': 'ursula', 'X-Roles': 'ROLE_USER']

    private static final Map<String, String> ADMIN = ['X-User': 'adam', 'X-Roles': 'ROLE_ADMIN']

    private static final Map<String, String> BILLING = ['X-User': 'bill', 'X-Roles': 'ROLE_BILLING']

    private static class Reply {
        int status
        String text
        Map<String, Object> body
    }

    private static ApplicationContext start(String route, Map<String, Object> more = [:], String... environments) {
        Map<String, Object> properties = [
                'micronaut.graphql.tools.allow.inert-directives'   : ['key'],
                'micronaut.graphql.tools.allow.unreachable'        : ['Order'],
                'micronaut.server.host'                            : '127.0.0.1',
                'micronaut.server.port'                            : -1,
                'micronaut.security.enabled'                       : true,
                'micronaut.security.intercept-url-map[0].pattern'  : '/graphql',
                'micronaut.security.intercept-url-map[0].access[0]': route
        ] as Map<String, Object>
        properties.putAll(more)
        List<String> names = [FederationFixtures.FEDERATION] + environments.toList()
        ApplicationContext context = ApplicationContext.builder(names as String[]).properties(properties).start()
        context.getBean(EmbeddedServer).start()
        return context
    }

    private static Reply post(ApplicationContext context, Map<String, String> headers, String query) {
        EmbeddedServer server = context.getBean(EmbeddedServer)
        JsonMapper json = context.getBean(JsonMapper)
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://${server.host}:${server.port}/graphql".toString()))
                .header('Content-Type', 'application/json')
                .header('Accept', 'application/json')
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString([query: query])))
        headers.each { String name, String value -> request.header(name, value) }
        HttpClient client = HttpClient.newHttpClient()
        try {
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString())
            Map<String, Object> body = response.statusCode() == 200
                    ? json.readValue(response.body(), Argument.mapOf(String, Object)) : [:] as Map<String, Object>
            return new Reply(status: response.statusCode(), text: response.body(), body: body)
        } finally {
            client.close()
        }
    }

    private static Object path(Object value, Object... keys) {
        Object current = value
        for (Object key : keys) {
            if (key instanceof Integer) {
                current = current instanceof List && (key as int) < ((List) current).size() ? ((List) current).get(key as int) : null
            } else {
                current = current instanceof Map ? ((Map) current).get(key) : null
            }
        }
        return current
    }

    private static boolean forbidden(Reply reply) {
        return reply.status == 200 && reply.body.get('data') == null &&
                path(reply.body, 'errors', 0, 'extensions', 'code') == 'FORBIDDEN'
    }

    // tag::route[]
    void 'a route rule stops anonymous callers before any field runs'() {
        given:
        ApplicationContext context = start('isAuthenticated()')

        when:
        Reply service = post(context, ANONYMOUS, SERVICE)
        Reply entities = post(context, ANONYMOUS, PRODUCT)
        Reply admin = post(context, ADMIN, PRODUCT)

        then:
        service.status == 401
        !service.text.contains('type Product')
        entities.status == 401
        !entities.text.contains('Product p1')
        forbidden(post(context, USER, SERVICE))
        forbidden(post(context, USER, PRODUCT))
        path(admin.body, 'data', '_entities', 0, 'name') == 'Product p1'

        cleanup:
        context?.close()
    }
    // end::route[]

    // tag::guard[]
    void 'the guard decides for each entity type'() {
        given:
        ApplicationContext context = start('isAnonymous()')

        when:
        Reply product = post(context, ADMIN, PRODUCT)
        Reply order = post(context, BILLING, ORDER)
        Reply mixed = post(context, ADMIN, '{ _entities(representations: [{__typename: "Product", id: "p1"},' +
                ' {__typename: "Order", id: "o1"}]) { __typename } }')

        then:
        forbidden(post(context, ANONYMOUS, PRODUCT))
        forbidden(post(context, USER, PRODUCT))
        forbidden(post(context, ADMIN, ORDER))
        forbidden(post(context, BILLING, PRODUCT))
        product.body.get('data') == [_entities: [[__typename: 'Product', id: 'p1', name: 'Product p1']]]
        order.body.get('data') == [_entities: [[__typename: 'Order', id: 'o1', total: 42]]]
        forbidden(mixed)

        cleanup:
        context?.close()
    }
    // end::guard[]

    // tag::secured-and-entities[]
    void 'secured on a resolver does not reach entities but the guard does'() {
        given:
        String field = '{ product(id: "p1") { id name } }'
        ApplicationContext open = start('isAnonymous()', [:], FederationFixtures.UNGUARDED)
        ApplicationContext guarded = start('isAnonymous()')

        when:
        Reply secured = post(open, ANONYMOUS, field)
        Reply entities = post(open, ANONYMOUS, PRODUCT)
        Reply allowed = post(guarded, ADMIN, field)

        then:
        path(secured.body, 'data', 'product') == null
        path(secured.body, 'errors', 0, 'extensions', 'code') == 'UNAUTHENTICATED'
        path(entities.body, 'data', '_entities', 0, 'name') == 'Product p1'
        forbidden(post(guarded, ANONYMOUS, PRODUCT))
        path(allowed.body, 'data', 'product', 'name') == 'Product p1'

        cleanup:
        open?.close()
        guarded?.close()
    }
    // end::secured-and-entities[]

    // tag::service[]
    void 'the service field returns the sdl with introspection off to a caller the guard lets through'() {
        given:
        ApplicationContext context = start('isAnonymous()', ['micronaut.graphql.tools.protection.introspection': false])

        when:
        Reply schema = post(context, ADMIN, '{ __schema { queryType { name } } }')
        Reply service = post(context, ADMIN, SERVICE)

        then:
        schema.body.get('data') == null
        !((List) schema.body.get('errors')).empty
        String.valueOf(path(service.body, 'data', '_service', 'sdl')).contains('type Product @key')
        forbidden(post(context, ANONYMOUS, SERVICE))
        forbidden(post(context, BILLING, SERVICE))

        cleanup:
        context?.close()
    }
    // end::service[]

}
