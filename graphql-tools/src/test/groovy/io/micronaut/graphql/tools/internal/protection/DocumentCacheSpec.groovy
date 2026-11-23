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
package io.micronaut.graphql.tools.internal.protection

import graphql.ExecutionInput
import graphql.execution.preparsed.PreparsedDocumentEntry
import graphql.language.Document
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.PropagatedContextElement
import spock.lang.Specification

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function

/** The parsed-document cache: concurrent first misses for one text share one parse, and the entry is shared. */
class DocumentCacheSpec extends Specification {

    void "concurrent first misses for one text parse once and share the entry"() {
        given:
        def cache = new DocumentCache(10)
        def parses = new AtomicInteger()
        def started = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        Function<ExecutionInput, PreparsedDocumentEntry> parse = { ExecutionInput input ->
            parses.incrementAndGet()
            started.countDown()
            release.await(10, TimeUnit.SECONDS)
            new PreparsedDocumentEntry(Document.newDocument().build())
        }
        def pool = Executors.newFixedThreadPool(8)

        when:
        def first = pool.submit { cache.getDocumentAsync(ExecutionInput.newExecutionInput('{ a }').build(), parse).get() }
        started.await(10, TimeUnit.SECONDS)
        def others = (1..7).collect {
            pool.submit { cache.getDocumentAsync(ExecutionInput.newExecutionInput('{ a }').build(), parse).get() }
        }
        Thread.sleep(200)
        release.countDown()
        def entries = [first.get(10, TimeUnit.SECONDS)] + others.collect { it.get(10, TimeUnit.SECONDS) }

        then:
        parses.get() == 1
        entries.every { it.is(entries[0]) }
        cache.size() == 1

        cleanup:
        pool.shutdownNow()
    }

    void "a failed parse is rethrown and not kept"() {
        given:
        def cache = new DocumentCache(10)
        def calls = new AtomicInteger()
        Function<ExecutionInput, PreparsedDocumentEntry> parse = { ExecutionInput input ->
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException('boom')
            }
            new PreparsedDocumentEntry(Document.newDocument().build())
        }
        def input = ExecutionInput.newExecutionInput('{ a }').build()

        when:
        cache.getDocumentAsync(input, parse)

        then:
        thrown(IllegalStateException)

        when:
        def entry = cache.getDocumentAsync(input, parse).get()

        then:
        entry != null
        calls.get() == 2
    }

    void "a later request receives the entry of the first one"() {
        given:
        def cache = new DocumentCache(10)
        Function<ExecutionInput, PreparsedDocumentEntry> parse = { new PreparsedDocumentEntry(Document.newDocument().build()) }

        when:
        def first = cache.getDocumentAsync(ExecutionInput.newExecutionInput('{ a }').build(), parse).get()
        def second = cache.getDocumentAsync(ExecutionInput.newExecutionInput('{ a }').build(), parse).get()

        then:
        first.is(second)
    }

    static final class Marker implements PropagatedContextElement {
        final int id
        Marker(int id) { this.id = id }
    }

    void "each waiter continues under its own propagated context, not the owner's"() {
        given:
        def cache = new DocumentCache(10)
        def started = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        Function<ExecutionInput, PreparsedDocumentEntry> parse = { ExecutionInput input ->
            started.countDown()
            release.await(10, TimeUnit.SECONDS)
            new PreparsedDocumentEntry(Document.newDocument().build())
        }
        def seen = new ConcurrentHashMap<Integer, Integer>()
        def pool = Executors.newFixedThreadPool(8)
        def request = { int id ->
            pool.submit {
                try (def scope = PropagatedContext.empty().plus(new Marker(id)).propagate()) {
                    def future = cache.getDocumentAsync(ExecutionInput.newExecutionInput('{ a }').build(), parse)
                    future.thenApply { entry ->
                        seen.put(id, PropagatedContext.get().find(Marker).map { it.id }.orElse(-1))
                        entry
                    }.get(10, TimeUnit.SECONDS)
                }
            }
        }

        when:
        def futures = [request(0)]
        started.await(10, TimeUnit.SECONDS)
        futures += (1..6).collect { request(it) }
        Thread.sleep(300)
        release.countDown()
        futures.each { it.get(10, TimeUnit.SECONDS) }

        then:
        seen.size() == 7
        seen.every { id, found -> id == found }

        cleanup:
        pool.shutdownNow()
    }
}
