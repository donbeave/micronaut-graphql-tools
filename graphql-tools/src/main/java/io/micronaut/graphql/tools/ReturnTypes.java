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
package io.micronaut.graphql.tools;

import graphql.execution.DataFetcherResult;
import graphql.language.NonNullType;
import graphql.language.Type;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.graphql.tools.exceptions.UnsupportedReturnTypeException;
import org.reactivestreams.Publisher;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import static io.micronaut.graphql.tools.GraphQLUtils.printType;

/**
 * The containers graphql-java resolves at runtime around the value of a field, and how a declared return type is
 * unwrapped to the type the GraphQL type is mapped to.
 */
@Internal
final class ReturnTypes {

    private static final List<Class<?>> RETURN_TYPE_WRAPPERS =
            List.of(CompletionStage.class, Publisher.class, DataFetcherResult.class, Optional.class);

    private ReturnTypes() {
    }

    /**
     * Removes the wrappers graphql-java resolves at runtime from a declared return type, in the order graphql-java
     * resolves them: {@link CompletionStage} (field level only, graphql-java does not await list elements), then
     * {@link Publisher} (required on fields of the subscription type and allowed nowhere else; the rest applies to each
     * event), then {@link DataFetcherResult} (its data, errors and local context are applied), then {@link Optional}
     * (an empty one becomes {@code null}, so it needs a nullable GraphQL type). Each wrapper may appear at most once.
     * This is stricter than graphql-java in one place: {@code DataFetcherResult<Publisher<T>>} on a subscription field is
     * rejected (graphql-java would unwrap it), so a subscription field returns the {@link Publisher} directly.
     *
     * @param argument       the declared return type
     * @param graphQlType    the GraphQL type of the field or the list element, before removing non-null
     * @param fieldLevel     {@code true} for the field type itself, {@code false} for a list element
     * @param subscription   {@code true} for a field of the subscription type
     * @param mappingContext the mapping context
     * @return the type the GraphQL type has to be mapped to
     */
    static Argument<?> unwrap(Argument<?> argument, Type<?> graphQlType, boolean fieldLevel,
                                                boolean subscription, TypeMappingContext mappingContext) {
        Argument<?> result = argument;
        Class<?> outer = null;

        if (CompletionStage.class.isAssignableFrom(result.getType())) {
            if (!fieldLevel) {
                throw new UnsupportedReturnTypeException(mappingContext,
                        CompletionStage.class.getName() + " is not allowed inside a list.");
            }
            outer = CompletionStage.class;
            result = typeArgument(result, CompletionStage.class, mappingContext);
        }

        boolean publisher = Publisher.class.isAssignableFrom(result.getType());

        if (fieldLevel && subscription) {
            if (!publisher) {
                throw new UnsupportedReturnTypeException(mappingContext, "a field of the subscription type must return "
                        + Publisher.class.getName() + " (optionally inside a CompletionStage), found "
                        + result.getType().getName() + ".");
            }
            outer = Publisher.class;
            result = typeArgument(result, Publisher.class, mappingContext);
        } else if (publisher) {
            throw new UnsupportedReturnTypeException(mappingContext,
                    Publisher.class.getName() + " is only allowed on fields of the subscription type.");
        }

        if (DataFetcherResult.class.isAssignableFrom(result.getType())) {
            outer = DataFetcherResult.class;
            result = typeArgument(result, DataFetcherResult.class, mappingContext);
        }

        if (Optional.class.equals(result.getType())) {
            if (graphQlType instanceof NonNullType) {
                throw new UnsupportedReturnTypeException(mappingContext, Optional.class.getName()
                        + " is only allowed for a nullable GraphQL type, the field type is "
                        + printType(graphQlType) + ".");
            }
            outer = Optional.class;
            result = typeArgument(result, Optional.class, mappingContext);
        }

        if (outer != null) {
            Class<?> inner = result.getType();

            for (Class<?> wrapper : RETURN_TYPE_WRAPPERS) {
                if (wrapper.isAssignableFrom(inner)) {
                    throw new UnsupportedReturnTypeException(mappingContext, outer.getName() + " cannot wrap "
                            + wrapper.getName() + ". Supported wrappers, outermost first: CompletionStage "
                            + "(field level only), Publisher (subscription fields only), DataFetcherResult, Optional "
                            + "(nullable types only), each at most once.");
                }
            }
        }

        return result;
    }

    private static Argument<?> typeArgument(Argument<?> argument, Class<?> wrapper,
                                            TypeMappingContext mappingContext) {
        return argument.getFirstTypeVariable()
                .orElseThrow(() -> new UnsupportedReturnTypeException(mappingContext,
                        wrapper.getName() + " must declare the type it wraps."));
    }

}
