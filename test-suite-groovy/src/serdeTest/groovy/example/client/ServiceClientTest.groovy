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
package example.client

import example.client.ServiceClient.Viewer
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.graphql.tools.client.GraphQLResponse
import io.micronaut.graphql.tools.client.exceptions.GraphQLTransportException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class ServiceClientTest extends Specification implements TestPropertyProvider {

    @Inject
    ServiceClient client

    @Inject
    ExampleClient exampleClient

    @Inject
    RequestRecorder recorder

    // tag::discovery[]
    @Override
    Map<String, String> getProperties() {
        int port = SocketUtils.findAvailableTcpPort()
        [
                'micronaut.server.port'               : String.valueOf(port),
                'micronaut.http.services.example.urls': "http://127.0.0.1:${port}".toString()
        ]
    }
    // end::discovery[]

    def 'a header parameter is sent'() {
        expect:
        // tag::headers[]
        Viewer viewer = client.viewer('bob')

        viewer == new Viewer('bob')
        recorder.header('X-User') == 'bob'
        // end::headers[]
        Viewer guest = client.viewer(null)
        guest == null
        recorder.header('X-User') == null
    }

    def 'a cookie parameter is sent'() {
        expect:
        // tag::cookie[]
        Viewer viewer = client.viewerOfSession('abc')

        recorder.header('Cookie') == 'session=abc'
        // end::cookie[]
    }

    def 'the filter of the service id runs on the calls of that service'() {
        expect:
        // tag::filter[]
        Viewer viewer = client.viewer('bob')
        recorder.header('X-Service-Filter') == 'example'

        GraphQLResponse<String> introduced = exampleClient.introduced()
        recorder.header('X-Service-Filter') == null
        // end::filter[]
    }

    def 'a query is retried'() {
        expect:
        // tag::retry[]
        int before = recorder.requests()
        recorder.failNext(2)
        List<Viewer> viewers = client.profiles('bob')

        viewers == [new Viewer('bob')]
        recorder.requests() == before + 3
        // end::retry[]
        recorder.failNext(0)

        when:
        recorder.failNext(1)
        client.profilesOnce('bob')

        then:
        thrown(GraphQLTransportException)
        client.profilesOnce('bob') == [new Viewer('bob')]
    }
}
