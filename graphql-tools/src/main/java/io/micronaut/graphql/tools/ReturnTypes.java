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
import io.micronaut.graphql.tools.binding.internal.schema.WrapperRules;
import io.micronaut.graphql.tools.exceptions.UnsupportedReturnTypeException;
import org.reactivestreams.Publisher;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;

import static io.micronaut.graphql.tools.GraphQLUtils.printType;

@Internal
final class ReturnTypes {

    private static final List<Class<?>> RETURN_TYPE_WRAPPERS =
            List.of(CompletionStage.class, Publisher.class, DataFetcherResult.class, Optional.class);

    // graphql-java's DefaultValueUnboxer unwraps these at runtime, like Optional
    private static final Map<Class<?>, Class<?>> PRIMITIVE_OPTIONALS = Map.of(
            OptionalInt.class, Integer.class, OptionalLong.class, Long.class, OptionalDouble.class, Double.class);

    private ReturnTypes() {
    }

    static Argument<?> unwrap(Argument<?> argument, Type<?> graphQlType, boolean fieldLevel,
                              boolean subscription, TypeMappingContext mappingContext) {
        Argument<?> result = argument;
        Class<?> outer = null;

        if (CompletionStage.class.isAssignableFrom(result.getType())) {
            if (!fieldLevel) {
                throw new UnsupportedReturnTypeException(mappingContext, WrapperRules.completionStageInList());
            }
            outer = CompletionStage.class;
            result = typeArgument(result, CompletionStage.class, mappingContext);
        }

        boolean publisher = Publisher.class.isAssignableFrom(result.getType());

        if (fieldLevel && subscription) {
            if (!publisher) {
                throw new UnsupportedReturnTypeException(mappingContext,
                        WrapperRules.subscriptionNeedsPublisher(result.getType().getName()));
            }
            outer = Publisher.class;
            result = typeArgument(result, Publisher.class, mappingContext);
        } else if (publisher) {
            throw new UnsupportedReturnTypeException(mappingContext, WrapperRules.publisherOutsideSubscription());
        }

        if (DataFetcherResult.class.isAssignableFrom(result.getType())) {
            outer = DataFetcherResult.class;
            result = typeArgument(result, DataFetcherResult.class, mappingContext);
        }

        if (Optional.class.equals(result.getType())) {
            if (graphQlType instanceof NonNullType) {
                throw new UnsupportedReturnTypeException(mappingContext,
                        WrapperRules.optionalAtNonNull(printType(graphQlType)));
            }
            outer = Optional.class;
            result = typeArgument(result, Optional.class, mappingContext);
        }

        Class<?> primitiveOptional = PRIMITIVE_OPTIONALS.get(result.getType());
        if (primitiveOptional != null) {
            if (graphQlType instanceof NonNullType) {
                throw new UnsupportedReturnTypeException(mappingContext,
                        WrapperRules.primitiveOptionalAtNonNull(result.getType().getName(), printType(graphQlType)));
            }
            if (Optional.class.equals(outer)) {
                throw new UnsupportedReturnTypeException(mappingContext,
                        WrapperRules.optionalCannotWrapPrimitiveOptional(result.getType().getName()));
            }
            result = Argument.of(primitiveOptional);
        }

        if (outer != null) {
            Class<?> inner = result.getType();

            for (Class<?> wrapper : RETURN_TYPE_WRAPPERS) {
                if (wrapper.isAssignableFrom(inner)) {
                    throw new UnsupportedReturnTypeException(mappingContext,
                            WrapperRules.cannotWrap(outer.getName(), wrapper.getName()));
                }
            }
        }

        return result;
    }

    private static Argument<?> typeArgument(Argument<?> argument, Class<?> wrapper,
                                            TypeMappingContext mappingContext) {
        return argument.getFirstTypeVariable()
                .orElseThrow(() -> new UnsupportedReturnTypeException(mappingContext,
                        WrapperRules.missingTypeArgument(wrapper.getName())));
    }

    static String convertedTypeName(Argument<?> declaredType, boolean subscription, boolean returnType) {
        Argument<?> result = declaredType;
        if (CompletionStage.class.isAssignableFrom(result.getType()) || Future.class.isAssignableFrom(result.getType())) {
            result = result.getFirstTypeVariable().orElse(result);
        }
        if (subscription && Publisher.class.isAssignableFrom(result.getType())) {
            result = result.getFirstTypeVariable().orElse(result);
        }
        if (DataFetcherResult.class.isAssignableFrom(result.getType())) {
            result = result.getFirstTypeVariable().orElse(result);
        }
        if (Optional.class.equals(result.getType())) {
            result = result.getFirstTypeVariable().orElse(result);
        }
        if (returnType && PRIMITIVE_OPTIONALS.containsKey(result.getType())) {
            result = Argument.of(PRIMITIVE_OPTIONALS.get(result.getType()));
        }
        Argument<?>[] typeParameters = result.getTypeParameters();
        if (Iterable.class.isAssignableFrom(result.getType()) && typeParameters.length == 1
                && DataFetcherResult.class.isAssignableFrom(typeParameters[0].getType())
                && typeParameters[0].getTypeParameters().length == 1) {
            return result.getType().getTypeName() + "<" + JavaTypeNames.of(typeParameters[0].getTypeParameters()[0])
                    + ">";
        }
        return JavaTypeNames.of(result);
    }

}
