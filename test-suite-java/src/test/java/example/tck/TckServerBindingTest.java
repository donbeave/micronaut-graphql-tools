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
package example.tck;

import io.micronaut.context.ApplicationContext;
import io.micronaut.graphql.tools.tck.Tck;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The server of a scenario binds the loopback address that its client connects to. On a wildcard address another process
 * that listens on the same port of the loopback address (a Gradle daemon does) takes the connections, and the request
 * fails with {@code header parser received no bytes} (#374). The context ignores the test environment, whose
 * properties also set the host, so that only {@code Tck.applicationProperties} decides it.
 */
class TckServerBindingTest {

    @Test
    void anotherListenerCannotTakeThePortOfTheScenarioServer() throws IOException {
        try (ApplicationContext context = ApplicationContext.builder().deduceEnvironment(false)
                .properties(Tck.applicationProperties("bean-scopes")).start()) {
            EmbeddedServer server = context.getBean(EmbeddedServer.class).start();
            assertEquals("127.0.0.1", server.getHost());

            try (ServerSocket other = new ServerSocket()) {
                other.setReuseAddress(true);
                assertThrows(BindException.class,
                        () -> other.bind(new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), server.getPort())));
            }
        }
    }

}
