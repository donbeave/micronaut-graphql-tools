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
package io.micronaut.graphql.tools.binding.internal.rules;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;

import java.util.List;

/**
 * What {@link ResolverClassRules#arguments} found for one resolver method: the parameters that bind, in order, and the
 * first violation, if any. The slots before the violation are still reported, because a caller that binds each slot
 * (and so reads its type) reports a violation in that type before it reports a later one.
 *
 * @param slots   what each parameter before the violation binds, in parameter order
 * @param failure the first violation, or {@code null}
 */
@Internal
public record ArgumentVerdict(List<Slot> slots, @Nullable Failure failure) {

    public ArgumentVerdict {
        slots = List.copyOf(slots);
    }

    /**
     * What a parameter binds.
     */
    public enum SlotKind {
        /** The source object of a type resolver method: always the first parameter. */
        SOURCE,
        /** An argument of the field. */
        ARGUMENT,
        /** The {@code DataFetchingEnvironment}: always the last parameter. */
        ENVIRONMENT,
        /** A {@code DataLoader<K, V>} of the execution: in any position after the source. */
        DATA_LOADER
    }

    /**
     * @param kind      what the parameter binds
     * @param parameter the index of the parameter in the method
     * @param argument  the name of the schema argument for {@link SlotKind#ARGUMENT}, otherwise {@code null}
     */
    public record Slot(SlotKind kind, int parameter, @Nullable String argument) {
    }

    /**
     * A violation of the argument rules.
     */
    public sealed interface Failure permits CountMismatch, InvalidSource, ParameterWithoutArgument, DuplicateParameter, UntypedDataLoader {
    }

    /**
     * The method has too many or too few parameters for the field.
     *
     * @param tooMany  whether the method has more parameters than the field needs
     * @param found    the number of parameters that are not the environment
     * @param required the number of parameters that the field needs
     */
    public record CountMismatch(boolean tooMany, int found, int required) implements Failure {
    }

    /**
     * The first parameter of a type resolver method is not the source type.
     *
     * @param parameter the index of the parameter
     */
    public record InvalidSource(int parameter) implements Failure {
    }

    /**
     * A parameter names no argument of the field.
     *
     * @param parameter the index of the parameter
     * @param name      the name the parameter binds (its own name or its {@code @GraphQLArgument} value)
     * @param available the argument names of the field, in schema order
     * @param syntheticName whether {@code name} is the parameter's own name and the class file did not carry it
     */
    public record ParameterWithoutArgument(int parameter, String name, List<String> available,
                                           boolean syntheticName) implements Failure {

        public ParameterWithoutArgument {
            available = List.copyOf(available);
        }
    }

    /**
     * Two parameters bind the same argument.
     *
     * @param name the argument name
     */
    public record DuplicateParameter(String name) implements Failure {
    }

    /**
     * A {@code DataLoader} parameter without its key and value types: the library picks the loader by those types.
     *
     * @param parameter the index of the parameter
     */
    public record UntypedDataLoader(int parameter) implements Failure {
    }
}
