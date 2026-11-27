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
package io.micronaut.graphql.tools.client.call

import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.core.type.Argument
import io.micronaut.graphql.tools.client.GraphQLHttpClient
import io.micronaut.graphql.tools.client.GraphQLMediaTypes
import io.micronaut.graphql.tools.client.GraphQLRequest
import io.micronaut.graphql.tools.client.OperationType
import io.micronaut.graphql.tools.client.exceptions.GraphQLClientException
import io.micronaut.graphql.tools.client.exceptions.GraphQLTransportException
import io.micronaut.http.HttpMethod
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.serde.ObjectMapper
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

class GraphQLHttpClientSpec extends Specification {

    @Shared int port = SocketUtils.findAvailableTcpPort()
    @Shared int secondPort = SocketUtils.findAvailableTcpPort()
    @Shared int deadPort = SocketUtils.findAvailableTcpPort()
    @Shared @AutoCleanup EmbeddedServer second = ApplicationContext.run(EmbeddedServer, ['micronaut.server.port': secondPort])
    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run(EmbeddedServer, [
            'micronaut.server.port'                     : port,
            'micronaut.http.services.stub.url'          : "http://localhost:$port",
            'micronaut.http.services.slow.url'          : "http://localhost:$port",
            'micronaut.http.services.slow.read-timeout' : '300ms',
            'micronaut.http.services.balanced.urls'     : ["http://localhost:$port", "http://localhost:$secondPort"],
            'micronaut.http.services.dead.url'          : "http://localhost:$deadPort",
            'micronaut.http.services.prefixed.url'      : "http://localhost:$port/api",
    ]).applicationContext

    @Shared Clients clients = context.getBean(Clients)
    @Shared StubController server = context.getBean(StubController)
    @Shared StubController secondServer = second.applicationContext.getBean(StubController)
    @Shared ObjectMapper mapper = context.getBean(ObjectMapper)

    private static Consumer headers(Object status, String kind) {
        { it.header('X-Status', status.toString()).header('X-Kind', kind) } as Consumer
    }

    private static GraphQLRequest<Map<String, Object>> query() {
        GraphQLRequest.builder().document('{ a }').build()
    }

    @Unroll
    void "a #status response with a #kind body is #expected"() {
        when:
        def response
        def failure
        try {
            response = clients.stub.executeBlocking(query(), headers(status, kind))
        } catch (GraphQLClientException e) {
            failure = e
        }

        then:
        switch (expected) {
            case 'a response':
                assert failure == null
                assert response.httpResponse().code() == status
                assert response.hasData() || !response.errors().empty
                break
            case 'a transport failure':
                assert failure instanceof GraphQLTransportException
                assert failure.code == 'transport-error'
                assert failure.status == status
                assert !failure.timeout
                break
            case 'a decode error':
                assert !(failure instanceof GraphQLTransportException)
                assert failure.code == 'decode-error'
                break
        }

        where:
        [status, kind] << [[200, 294, 400, 401, 404, 405, 500, 503, 300, 304], StubController.KIND_NAMES].combinations()
        expected = classify(status, kind)
    }

    static String classify(int status, String kind) {
        boolean graphql = kind in ['data', 'errors', 'partial', 'null-data']
        if (status in [200, 294]) {
            return graphql ? 'a response' : kind in ['truncated', 'wrong-shape'] ? 'a decode error' : 'a transport failure'
        }
        if (status in [400, 401, 405, 500, 503]) {
            return graphql ? 'a response' : 'a transport failure'
        }
        'a transport failure'
    }

    void "the request is a POST with the JSON body and the GraphQL Accept header"() {
        when:
        def request = GraphQLRequest.builder().document('query Q($id: ID) { a(id: $id) }').operationName('Q')
                .variable('id', null).variable('n', 7).extension('persisted', [hash: 'x']).build()
        def response = clients.stub.executeBlocking(request)
        def seen = server.last()

        then:
        response.data() == [a: 1]
        response.dataOrThrow() == [a: 1]
        response.httpResponse().code() == 200
        seen.method() == 'POST'
        seen.headers()['content-type'] == ['application/json']
        seen.headers()['accept'] == [GraphQLMediaTypes.ACCEPT]
        mapper.readValue(seen.body(), Argument.mapOf(String, Object)) ==
                [query: 'query Q($id: ID) { a(id: $id) }', operationName: 'Q', variables: [id: null, n: 7], extensions: [persisted: [hash: 'x']]]
    }

    void "a request without a name, variables or extensions sends the query only"() {
        when:
        clients.stub.executeBlocking(query())

        then:
        mapper.readValue(server.last().body(), Argument.mapOf(String, Object)) == [query: '{ a }']
    }

    void "a query sent with GET carries URL-encoded parameters"() {
        given:
        def document = 'query Q($s: String) { a(s: $s) { b & c } } # é + %'
        def request = GraphQLRequest.builder().document(document).operationName('Q').variable('s', 'x&y=z +1')
                .extension('e', true).httpMethod(HttpMethod.GET).build()

        when:
        def response = clients.stub.executeBlocking(request)
        def seen = server.last()

        then:
        response.data() == [a: 1]
        seen.method() == 'GET'
        seen.body() == null
        seen.headers()['accept'] == [GraphQLMediaTypes.ACCEPT]
        seen.uri().startsWith('/graphql?query=query%20Q')
        seen.parameters()['query'] == [document]
        seen.parameters()['operationName'] == ['Q']
        mapper.readValue(seen.parameters()['variables'][0], Argument.mapOf(String, Object)) == [s: 'x&y=z +1']
        mapper.readValue(seen.parameters()['extensions'][0], Argument.mapOf(String, Object)) == [e: true]
    }

    void "a query sent with GET leaves out the parameters it does not have"() {
        when:
        clients.stub.executeBlocking(GraphQLRequest.builder().document('{ a }').httpMethod(HttpMethod.GET).build())

        then:
        server.last().parameters().keySet() == ['query'] as Set
    }

    void "only a query can be sent with GET"() {
        when:
        GraphQLRequest.builder().document('mutation { m }').operationType(OperationType.MUTATION).httpMethod(HttpMethod.GET).build()

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('Only a query')
    }

    void "only POST and GET send a request"() {
        when:
        GraphQLRequest.builder().httpMethod(HttpMethod.PUT)

        then:
        thrown(IllegalArgumentException)
    }

    void "a subscription is not sent over HTTP"() {
        when:
        clients.stub.executeBlocking(GraphQLRequest.builder().document('subscription { s }').operationType(OperationType.SUBSCRIPTION).build())

        then:
        thrown(IllegalArgumentException)
    }

    void "the filter of the service id runs, and sees the operation attributes and the customizer"() {
        given:
        def before = StubFilter.CALLS.get()

        when:
        clients.stub.executeBlocking(GraphQLRequest.builder().document('mutation Add { m }').operationName('Add')
                .operationType(OperationType.MUTATION).build(), { it.header('X-Custom', 'yes') } as Consumer)
        def seen = server.last()

        then:
        StubFilter.CALLS.get() == before + 1
        seen.headers()['x-filter'] == ['ran']
        seen.headers()['x-custom'] == ['yes']
        seen.headers()['x-operation-name'] == ['Add']
        seen.headers()['x-operation-type'] == ['MUTATION']
    }

    void "a call to another service id does not run the filter"() {
        given:
        def before = StubFilter.CALLS.get()

        when:
        clients.balanced.executeBlocking(query())

        then:
        StubFilter.CALLS.get() == before
    }

    void "the future variant completes with the response and a typed data"() {
        when:
        def response = clients.stub.executeAsync(GraphQLRequest.builder(Argument.mapOf(String, Integer)).document('{ a }').build(), headers(200, 'partial')).get()

        then:
        response.data() == [a: null]
        response.errors()*.message() == ['p']
        response.errors()[0].path() == ['a']
    }

    void "the future variant reports a failure through the future"() {
        when:
        clients.stub.executeAsync(query(), headers(404, 'data')).join()

        then:
        def e = thrown(CompletionException)
        e.cause instanceof GraphQLTransportException
        e.cause.status == 404
        e.cause.headers != null
    }

    void "the future variant reports a request that cannot be sent through the future"() {
        when:
        clients.stub.executeAsync(GraphQLRequest.builder().document('subscription { s }').operationType(OperationType.SUBSCRIPTION).build()).join()

        then:
        def e = thrown(CompletionException)
        e.cause instanceof IllegalArgumentException
    }

    void "cancelling the future cancels the call"() {
        when:
        def future = clients.slow.executeAsync(query())
        future.cancel(true)
        future.join()

        then:
        thrown(CancellationException)
    }

    void "the reactive variant is cold, sends one request per subscription and emits one response"() {
        given:
        def publisher = clients.stub.execute(query(), headers(200, 'data'))
        def before = StubFilter.CALLS.get()

        expect:
        StubFilter.CALLS.get() == before

        when:
        def first = Mono.from(publisher).block()
        def second = Mono.from(publisher).block()

        then:
        first.data() == [a: 1]
        second.data() == [a: 1]
        StubFilter.CALLS.get() == before + 2
    }

    void "the reactive variant reports a failure as an error signal"() {
        when:
        Mono.from(clients.stub.execute(query(), headers(500, 'html'))).block()

        then:
        def e = thrown(GraphQLTransportException)
        e.status == 500
    }

    void "the reactive and the future variants complete on the event loop of the client"() {
        given:
        def gate = server.closeGate()
        def reactiveThread = new CompletableFuture<String>()
        def futureThread = new CompletableFuture<String>()
        Mono.from(clients.gated.execute(query())).subscribe { reactiveThread.complete(Thread.currentThread().name) }
        clients.gated.executeAsync(query()).thenAccept { futureThread.complete(Thread.currentThread().name) }

        when:
        gate.complete(true)
        def reactive = reactiveThread.get(10, TimeUnit.SECONDS)
        def future = futureThread.get(10, TimeUnit.SECONDS)

        then:
        assert reactive.toLowerCase().contains('eventloopgroup'): "the reactive variant completed on thread '$reactive'"
        assert future.toLowerCase().contains('eventloopgroup'): "the future variant completed on thread '$future'"
    }

    void "a blocking call on an event loop thread fails with its own code, and one on a worker thread works"() {
        given:
        def http = context.createBean(HttpClient, new URL("http://localhost:$port"))

        expect:
        http.toBlocking().retrieve('/blocks-on-the-event-loop') == 'blocking-on-event-loop'
        http.toBlocking().retrieve('/blocks-on-a-worker') == '{a=1}'

        cleanup:
        http.close()
    }

    void "a timeout is a transport failure with the timeout flag"() {
        when:
        clients.slow.executeBlocking(query())

        then:
        def e = thrown(GraphQLTransportException)
        e.timeout
        e.code == 'transport-timeout'
        e.status == GraphQLTransportException.NO_STATUS
    }

    void "a connection error is a transport failure without a status"() {
        when:
        clients.dead.executeBlocking(query())

        then:
        def e = thrown(GraphQLTransportException)
        !e.timeout
        e.code == 'transport-error'
        e.status == GraphQLTransportException.NO_STATUS
        e.cause != null
    }

    void "a service id with several urls balances the calls between them"() {
        given:
        def firstBefore = server.last()
        def secondBefore = secondServer.last()

        when:
        4.times { clients.balanced.executeBlocking(query()) }

        then:
        server.last() !== firstBefore
        secondServer.last() !== secondBefore
    }

    void "without an endpoint the request goes to /graphql"() {
        when:
        clients.stub.executeBlocking(query())

        then:
        server.last().uri() == '/graphql'
    }

    void "the endpoint of the annotation is the path of the request, for POST and for GET"() {
        when:
        clients.nested.executeBlocking(query())

        then:
        server.last().uri() == '/api/graphql'

        when:
        clients.nested.executeBlocking(GraphQLRequest.builder().document('{ a }').httpMethod(HttpMethod.GET).build())

        then:
        server.last().method() == 'GET'
        server.last().uri() == '/api/graphql?query=%7B%20a%20%7D'
    }

    void "the path of the service url is not used, the endpoint of the annotation is"() {
        when:
        clients.prefixed.executeBlocking(query())

        then:
        server.last().uri() == '/graphql'
    }
}
