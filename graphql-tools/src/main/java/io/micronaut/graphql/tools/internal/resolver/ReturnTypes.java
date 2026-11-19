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
package io.micronaut.graphql.tools.internal.resolver;

import graphql.execution.DataFetcherResult;
import graphql.language.Type;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.graphql.tools.TypeMappingContext;
import io.micronaut.graphql.tools.binding.internal.rules.ResolverClassRules;
import io.micronaut.graphql.tools.binding.internal.rules.ReturnVerdict;
import io.micronaut.graphql.tools.internal.JavaTypeNames;
import io.micronaut.graphql.tools.internal.exceptions.UnsupportedReturnTypeException;
import io.micronaut.graphql.tools.internal.model.RuntimeTypeModel;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import org.reactivestreams.Publisher;

/** Resolves the Java type that a resolver method returns, after unwrapping the reactive and optional wrappers. */
@Internal
public final class ReturnTypes {

    // graphql-java's DefaultValueUnboxer unwraps these at runtime, like Optional
    private static final Map<Class<?>, Class<?>> PRIMITIVE_OPTIONALS = Map.of(
            OptionalInt.class, Integer.class, OptionalLong.class, Long.class, OptionalDouble.class, Double.class);

    private ReturnTypes() {
    }

    // Peels the wrappers of a declared return type: ResolverClassRules.returnAdaptation decides, this follows its verdict
    // on the Argument and turns a violation into its exception.
    public static Argument<?> unwrap(RuntimeTypeModel typeModel, Argument<?> argument, Type<?> graphQlType,
                              boolean fieldLevel, boolean subscription, TypeMappingContext mappingContext) {
        ReturnVerdict verdict = ResolverClassRules.returnAdaptation(typeModel, typeModel.ref(argument), graphQlType,
                fieldLevel, subscription);
        if (verdict.failure() != null) {
            throw new UnsupportedReturnTypeException(mappingContext, verdict.failure());
        }
        Argument<?> result = argument;
        for (int i = 0; i < verdict.depth(); i++) {
            result = result.getFirstTypeVariable().get();
        }
        return verdict.primitiveOptional() ? Argument.of(PRIMITIVE_OPTIONALS.get(result.getType())) : result;
    }

    public static String convertedTypeName(Argument<?> declaredType, boolean subscription, boolean returnType) {
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
