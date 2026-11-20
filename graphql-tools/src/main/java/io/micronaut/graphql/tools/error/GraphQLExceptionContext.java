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
package io.micronaut.graphql.tools.error;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.execution.ResultPath;
import graphql.language.SourceLocation;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.core.annotation.NonNull;

import java.util.Map;

/**
 * What a {@link GraphQLExceptionMapper} knows about the failure besides the exception: the field that threw it.
 */
public interface GraphQLExceptionContext {

    /**
     * @return the data fetching environment of the field that failed: its arguments, source and GraphQL context
     */
    @NonNull
    DataFetchingEnvironment getEnvironment();

    /**
     * @return the response path of the field that failed
     */
    @NonNull
    ResultPath getPath();

    /**
     * @return the location of the field in the query document
     */
    @NonNull
    SourceLocation getSourceLocation();

    /**
     * Creates an error that has the path and the location of the field that failed and the classification
     * {@code DataFetchingException}. It needs no builder, which makes it the form for Kotlin too.
     *
     * @param message    the message that the client sees
     * @param extensions the extensions, for example a {@code code}; may be empty
     * @return the error
     */
    @NonNull
    GraphQLError error(@NonNull String message, @NonNull Map<String, Object> extensions);

    /**
     * Starts an error that has the path and the location of the field that failed and the classification
     * {@code DataFetchingException}. Set the message and the extensions, then call {@code build()}.
     *
     * @return a builder that has the path, the location and the classification set
     */
    @NonNull
    GraphqlErrorBuilder<?> newError();

}
