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
package io.micronaut.graphql.tools.opentelemetry

import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer

/** With the HTTP server integration of Micronaut Tracing, the request span is a child of the HTTP server span. */
class HttpParentSpec extends AbstractOpenTelemetrySpec {

    void "the request span is a child of the HTTP server span, and a resolver span is a child of the request span"() {
        given:
        start('OpenTelemetryHttp', ['micronaut.server.port': -1, 'micronaut.graphql.tools.execution.executor': 'blocking'])
        EmbeddedServer server = context.getBean(EmbeddedServer).start()
        HttpClient client = HttpClient.create(server.URL)

        when:
        def response = client.toBlocking().retrieve(HttpRequest.GET('/graphql?query=%7Btraced%7D'), String)
        def deadline = System.currentTimeMillis() + 5000
        while (finished().size() < 3 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
        def request = finished().find { it.name == 'query' }
        def http = finished().find { it.parentSpanId == '0000000000000000' || !finished()*.spanId.contains(it.parentSpanId) }
        def work = finished().find { it.name.endsWith('#resolver.work') }

        then:
        response.contains('traced')
        request != null
        http != null
        http.name != 'query'
        request.parentSpanId == http.spanId
        request.traceId == http.traceId
        work.parentSpanId == request.spanId

        cleanup:
        client?.close()
    }

}
