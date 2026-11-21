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
package io.micronaut.graphql.tools.upload.internal;

import graphql.schema.GraphQLScalarType;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.mapping.GraphQLScalarMapping;
import io.micronaut.graphql.tools.upload.GraphQLUpload;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.CompletedPart;
import jakarta.inject.Singleton;

/** Registers the mapping of the {@code Upload} scalar: an input only scalar that is bound to {@code CompletedFileUpload} or {@code CompletedPart}. */
@Internal
@Factory
@Requires(property = GraphQLUpload.PREFIX + ".enabled", notEquals = "false")
public final class UploadScalarFactory {

    @Bean
    @Singleton
    GraphQLScalarMapping upload() {
        GraphQLScalarType type = GraphQLScalarType.newScalar()
                .name(GraphQLUpload.SCALAR_NAME)
                .description("A file of a multipart request (GraphQL multipart request specification). Input only.")
                .coercing(new UploadCoercing())
                .build();
        return GraphQLScalarMapping.builder(type).inputClasses(CompletedFileUpload.class, CompletedPart.class).build();
    }

}
