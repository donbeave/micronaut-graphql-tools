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
package io.micronaut.graphql.tools.internal.protection;

import graphql.ExecutionInput;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import graphql.execution.preparsed.PreparsedDocumentProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.graphql.tools.internal.execution.ExecutionState;
import io.micronaut.core.util.clhm.ConcurrentLinkedHashMap;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * The library's {@link PreparsedDocumentProvider}: a bounded least-recently-used cache of parsed and validated documents,
 * keyed by the text of the query. A document that is in the cache is neither parsed nor validated again. The entry of a
 * query that failed to parse or validate is cached too, so that a repeated invalid query is cheap. The key is the text
 * only: variables and the operation name do not change the document, and the depth and complexity limits run after the
 * cache, on every execution. Concurrent first misses for one text share one parse: the first caller parses and the
 * others receive its result, each on a future of its own that completes under the propagated context and on the executor of
 * its own request. While an entry stays in the cache, later requests receive the same {@link PreparsedDocumentEntry}, so the parsed
 * document is shared across requests and must be treated as read-only.
 */
@Internal
public final class DocumentCache implements PreparsedDocumentProvider {

    private final ConcurrentLinkedHashMap<String, PreparsedDocumentEntry> documents;

    private final ConcurrentMap<String, CompletableFuture<PreparsedDocumentEntry>> parsing = new ConcurrentHashMap<>();

    /**
     * @param size the most documents that are kept, at least 1
     */
    public DocumentCache(int size) {
        this.documents = new ConcurrentLinkedHashMap.Builder<String, PreparsedDocumentEntry>()
                .maximumWeightedCapacity(size)
                .build();
    }

    @Override
    public CompletableFuture<PreparsedDocumentEntry> getDocumentAsync(ExecutionInput executionInput,
                                                                      Function<ExecutionInput, PreparsedDocumentEntry> parseAndValidate) {
        String query = executionInput.getQuery();
        PreparsedDocumentEntry entry = documents.get(query);
        if (entry == null) {
            return parseOnce(query, executionInput, parseAndValidate);
        }
        return CompletableFuture.completedFuture(entry);
    }

    private CompletableFuture<PreparsedDocumentEntry> parseOnce(String query, ExecutionInput executionInput,
                                                                Function<ExecutionInput, PreparsedDocumentEntry> parseAndValidate) {
        CompletableFuture<PreparsedDocumentEntry> mine = new CompletableFuture<>();
        CompletableFuture<PreparsedDocumentEntry> other = parsing.putIfAbsent(query, mine);
        if (other != null) {
            return resumeInOwnContext(other, executionInput);
        }
        try {
            PreparsedDocumentEntry entry = documents.get(query);
            if (entry == null) {
                entry = parseAndValidate.apply(executionInput);
                documents.put(query, entry);
            }
            mine.complete(entry);
        } catch (Throwable t) {
            mine.completeExceptionally(t);
            throw t;
        } finally {
            parsing.remove(query);
        }
        return mine;
    }

    /**
     * Gives a waiter a future of its own, completed under the propagated context and on the executor of its request, so
     * that the continuations graphql-java attaches run in the waiter's context and not in the one of the request that parses.
     */
    private static CompletableFuture<PreparsedDocumentEntry> resumeInOwnContext(CompletableFuture<PreparsedDocumentEntry> shared,
                                                                                ExecutionInput executionInput) {
        ExecutionState state = ExecutionState.of(executionInput.getGraphQLContext());
        PropagatedContext captured = state != null ? state.propagatedContext() : PropagatedContext.getOrEmpty();
        Executor executor = state != null ? state.requestExecutor() : null;
        CompletableFuture<PreparsedDocumentEntry> own = new CompletableFuture<>();
        shared.whenComplete((entry, failure) -> {
            if (executor == null) {
                completeIn(captured, own, entry, failure);
                return;
            }
            try {
                executor.execute(() -> completeIn(captured, own, entry, failure));
            } catch (RuntimeException e) {
                own.completeExceptionally(e);
            }
        });
        return own;
    }

    private static void completeIn(PropagatedContext captured, CompletableFuture<PreparsedDocumentEntry> own,
                                   PreparsedDocumentEntry entry, Throwable failure) {
        try (PropagatedContext.Scope ignored = captured.propagate()) {
            if (failure != null) {
                own.completeExceptionally(failure);
            } else {
                own.complete(entry);
            }
        }
    }

    /**
     * Present on graphql-java lines that declare it abstract; later lines dropped it.
     *
     * @param executionInput   the input
     * @param parseAndValidate parses and validates the query
     * @return the entry
     */
    public PreparsedDocumentEntry getDocument(ExecutionInput executionInput,
                                              Function<ExecutionInput, PreparsedDocumentEntry> parseAndValidate) {
        return getDocumentAsync(executionInput, parseAndValidate).join();
    }

    /**
     * @return the number of documents that are in the cache
     */
    public int size() {
        return documents.size();
    }
}
