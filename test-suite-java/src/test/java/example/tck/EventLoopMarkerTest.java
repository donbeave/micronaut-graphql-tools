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
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.http.netty.channel.NettyThreadFactory;
import io.micronaut.http.netty.configuration.NettyGlobalConfiguration;
import org.junit.jupiter.api.Test;
import reactor.core.scheduler.NonBlocking;

import java.util.concurrent.ThreadFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The async boundary recognises a Netty event-loop thread by the {@link NonBlocking} marker, which Micronaut's event-loop
 * threads carry while {@code micronaut.netty.default-thread-factory-reactor-non-blocking} is true. This pins that default.
 */
class EventLoopMarkerTest {

    @Test
    void theEventLoopThreadsOfMicronautAreNonBlockingByDefault() {
        try (ApplicationContext context = ApplicationContext.builder().properties(Tck.applicationProperties("execution-thread")).start()) {
            NettyGlobalConfiguration configuration = context.getBean(NettyGlobalConfiguration.class);
            assertTrue(configuration.isDefaultThreadFactoryReactorNonBlocking(),
                    "micronaut.netty.default-thread-factory-reactor-non-blocking must default to true");
            Thread thread = context.getBean(ThreadFactory.class, Qualifiers.byName(NettyThreadFactory.NAME)).newThread(() -> { });
            assertTrue(thread instanceof NonBlocking, "the thread of the default factory is " + thread.getClass().getName());
        }
    }

}
