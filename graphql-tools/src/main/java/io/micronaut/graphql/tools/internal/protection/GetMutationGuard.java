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
import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.OperationDefinition;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.execution.GraphQLTransport;
import io.micronaut.graphql.tools.internal.execution.ExecutionState;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * The library's {@link PreparsedDocumentProvider} when the endpoint serves {@code GET}: the document that the execution
 * parses (or takes from the {@link DocumentCache}) is the one whose operation type is checked, so nothing is parsed twice. A
 * {@code GET} or {@code HEAD} request whose selected operation is a mutation fails with {@link MutationOverGetException} before any
 * resolver runs. Every other request, and every operation of another transport, takes the delegate's path unchanged.
 */
@Internal
public final class GetMutationGuard implements PreparsedDocumentProvider {

    @Nullable
    private final PreparsedDocumentProvider delegate;

    /**
     * @param delegate the provider that parses and caches, or null to parse on every request
     */
    public GetMutationGuard(@Nullable PreparsedDocumentProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<PreparsedDocumentEntry> getDocumentAsync(ExecutionInput executionInput,
                                                                      Function<ExecutionInput, PreparsedDocumentEntry> parseAndValidate) {
        if (!isGet(executionInput)) {
            return delegate == null
                    ? CompletableFuture.completedFuture(parseAndValidate.apply(executionInput))
                    : delegate.getDocumentAsync(executionInput, parseAndValidate);
        }
        CompletableFuture<PreparsedDocumentEntry> parsed = delegate == null
                ? CompletableFuture.completedFuture(parseAndValidate.apply(executionInput))
                : delegate.getDocumentAsync(executionInput, parseAndValidate);
        if (parsed.isDone() && !parsed.isCompletedExceptionally()) {
            return check(parsed.join(), executionInput, parsed);
        }
        return parsed.thenCompose(entry -> check(entry, executionInput, CompletableFuture.completedFuture(entry)));
    }

    private static CompletableFuture<PreparsedDocumentEntry> check(PreparsedDocumentEntry entry, ExecutionInput input,
                                                                   CompletableFuture<PreparsedDocumentEntry> same) {
        Document document = entry.getDocument();
        if (document != null) {
            OperationDefinition operation = select(document, input.getOperationName());
            if (operation != null && operation.getOperation() == OperationDefinition.Operation.MUTATION) {
                return CompletableFuture.failedFuture(new MutationOverGetException(operation.getName()));
            }
        }
        return same;
    }

    @Nullable
    private static OperationDefinition select(Document document, @Nullable String operationName) {
        OperationDefinition only = null;
        List<Definition> definitions = document.getDefinitions();
        for (int i = 0; i < definitions.size(); i++) {
            if (definitions.get(i) instanceof OperationDefinition operation) {
                if (operationName != null && !operationName.isEmpty()) {
                    if (operationName.equals(operation.getName())) {
                        return operation;
                    }
                } else if (only == null) {
                    only = operation;
                } else {
                    return null;
                }
            }
        }
        return operationName == null || operationName.isEmpty() ? only : null;
    }

    private static boolean isGet(ExecutionInput input) {
        ExecutionState state = ExecutionState.of(input.getGraphQLContext());
        if (state == null || state.getTransport() != GraphQLTransport.HTTP) {
            return false;
        }
        HttpRequest<?> request = state.getHttpRequest();
        return request != null && (request.getMethod() == HttpMethod.GET || request.getMethod() == HttpMethod.HEAD);
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
}
