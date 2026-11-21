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
import io.micronaut.core.util.clhm.ConcurrentLinkedHashMap;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * The library's {@link PreparsedDocumentProvider}: a bounded least-recently-used cache of parsed and validated documents,
 * keyed by the text of the query. A document that is in the cache is neither parsed nor validated again. The entry of a
 * query that failed to parse or validate is cached too, so that a repeated invalid query is cheap. The key is the text
 * only: variables and the operation name do not change the document, and the depth and complexity limits run after the
 * cache, on every execution.
 */
@Internal
public final class DocumentCache implements PreparsedDocumentProvider {

    private final ConcurrentLinkedHashMap<String, PreparsedDocumentEntry> documents;

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
            entry = parseAndValidate.apply(executionInput);
            documents.put(query, entry);
        }
        return CompletableFuture.completedFuture(entry);
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
