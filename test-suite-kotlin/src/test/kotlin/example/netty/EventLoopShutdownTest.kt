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
package example.netty

import io.netty.buffer.ByteBufAllocator
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.util.concurrent.DefaultThreadFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Runs on the JVM and as a native binary (nativeTest): an event-loop thread that allocated a direct buffer releases it when
// the loop shuts down. Without shared-arena support in the image, Netty's cleaner fails on that thread (#568).
class EventLoopShutdownTest {

    @Test
    fun `shutting down an event loop releases its buffers`() {
        val failures = CopyOnWriteArrayList<Throwable>()
        val factory = DefaultThreadFactory("shutdown-test")
        val group = NioEventLoopGroup(1, ThreadFactory { runnable ->
            factory.newThread(runnable).also { thread -> thread.setUncaughtExceptionHandler { _, e -> failures.add(e) } }
        })
        group.submit { ByteBufAllocator.DEFAULT.directBuffer(1024).release() }.get(30, TimeUnit.SECONDS)
        group.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS)
        assertTrue(group.terminationFuture().await(30, TimeUnit.SECONDS), "the event loop never terminated")
        assertEquals(listOf<Throwable>(), failures.toList())
    }
}
