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
package io.micronaut.graphql.tools.internal.error;

import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.ResultPath;
import graphql.language.SourceLocation;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.error.GraphQLExceptionContext;

import java.util.Map;

/**
 * The context of one failed field, built from the parameters that graphql-java hands the exception handler.
 *
 * @param parameters the parameters of the handler
 */
@Internal
record FieldFailure(DataFetcherExceptionHandlerParameters parameters) implements GraphQLExceptionContext {

    @Override
    public DataFetchingEnvironment getEnvironment() {
        return parameters.getDataFetchingEnvironment();
    }

    @Override
    public ResultPath getPath() {
        return parameters.getPath();
    }

    @Override
    @Nullable
    public SourceLocation getSourceLocation() {
        return parameters.getSourceLocation();
    }

    @Override
    public GraphQLError error(String message, Map<String, Object> extensions) {
        return newError().message(message).extensions(extensions).build();
    }

    @Override
    public GraphqlErrorBuilder<?> newError() {
        GraphqlErrorBuilder<?> builder = GraphqlErrorBuilder.newError()
                .path(parameters.getPath())
                .errorType(ErrorType.DataFetchingException);
        if (parameters.getSourceLocation() != null) {
            builder.location(parameters.getSourceLocation());
        }
        return builder;
    }

}
