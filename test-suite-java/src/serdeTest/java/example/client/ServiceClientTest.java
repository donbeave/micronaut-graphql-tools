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
package example.client;

import example.client.ServiceClient.Viewer;
import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.graphql.tools.client.exceptions.GraphQLTransportException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServiceClientTest implements TestPropertyProvider {

    @Inject
    ServiceClient client;

    @Inject
    ExampleClient exampleClient;

    @Inject
    RequestRecorder recorder;

    // tag::discovery[]
    @Override
    public Map<String, String> getProperties() {
        int port = SocketUtils.findAvailableTcpPort();
        return Map.of(
                "micronaut.server.port", String.valueOf(port),
                "micronaut.http.services.example.urls", "http://127.0.0.1:" + port);
    }
    // end::discovery[]

    @Test
    void aHeaderParameterIsSent() {
        // tag::headers[]
        Viewer viewer = client.viewer("bob");

        assertEquals(new Viewer("bob"), viewer);
        assertEquals("bob", recorder.header("X-User"));
        // end::headers[]
        assertNull(client.viewer(null));
        assertNull(recorder.header("X-User"));
    }

    @Test
    void aCookieParameterIsSent() {
        // tag::cookie[]
        client.viewerOfSession("abc");

        assertEquals("session=abc", recorder.header("Cookie"));
        // end::cookie[]
    }

    @Test
    void theFilterOfTheServiceIdRunsOnTheCallsOfThatService() {
        // tag::filter[]
        client.viewer("bob");
        assertEquals("example", recorder.header("X-Service-Filter"));

        exampleClient.introduced();
        assertNull(recorder.header("X-Service-Filter"));
        // end::filter[]
    }

    @Test
    void aQueryIsRetried() {
        // tag::retry[]
        int before = recorder.requests();
        recorder.failNext(2);

        assertEquals(List.of(new Viewer("bob")), client.profiles("bob"));
        assertEquals(before + 3, recorder.requests());
        // end::retry[]

        recorder.failNext(1);
        assertThrows(GraphQLTransportException.class, () -> client.profilesOnce("bob"));
        assertEquals(List.of(new Viewer("bob")), client.profilesOnce("bob"));
    }

}
