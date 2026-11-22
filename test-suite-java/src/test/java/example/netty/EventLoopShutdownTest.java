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
package example.netty;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Runs on the JVM and as a native binary (nativeTest): an event-loop thread that allocated a direct buffer releases it when
// the loop shuts down. Without shared-arena support in the image, Netty's cleaner fails on that thread (#568).
class EventLoopShutdownTest {

    @Test
    void shuttingDownAnEventLoopReleasesItsBuffers() throws Exception {
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        DefaultThreadFactory factory = new DefaultThreadFactory("shutdown-test");
        NioEventLoopGroup group = new NioEventLoopGroup(1, runnable -> {
            Thread thread = factory.newThread(runnable);
            thread.setUncaughtExceptionHandler((t, e) -> failures.add(e));
            return thread;
        });
        group.submit(() -> ByteBufAllocator.DEFAULT.directBuffer(1024).release()).get(30, TimeUnit.SECONDS);
        group.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
        assertTrue(group.terminationFuture().await(30, TimeUnit.SECONDS), "the event loop never terminated");
        assertEquals(List.of(), failures);
    }

}
