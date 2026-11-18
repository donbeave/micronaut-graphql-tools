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
import io.micronaut.graphql.tools.binding.internal.model.AnnotationNames;
import io.micronaut.graphql.tools.binding.internal.model.JParameter;
import io.micronaut.graphql.tools.binding.internal.model.TypeRef;
import io.micronaut.graphql.tools.binding.internal.rules.ArgumentVerdict.CountMismatch;
import io.micronaut.graphql.tools.binding.internal.rules.ArgumentVerdict.DuplicateParameter;
import io.micronaut.graphql.tools.binding.internal.rules.ArgumentVerdict.InvalidSource;
import io.micronaut.graphql.tools.binding.internal.rules.ArgumentVerdict.ParameterWithoutArgument;
import io.micronaut.graphql.tools.binding.internal.rules.ArgumentVerdict.Slot;
import io.micronaut.graphql.tools.binding.internal.rules.ArgumentVerdict.SlotKind;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The rule bodies for one resolver method. Both adapters call them.
 */
@Internal
public final class ResolverClassRules {

    private static final String DATA_FETCHING_ENVIRONMENT = "graphql.schema.DataFetchingEnvironment";

    private ResolverClassRules() {
    }

    /**
     * Matches the parameters of a resolver method with the arguments of the field it binds: the source parameter first
     * (type resolver methods only), the {@code DataFetchingEnvironment} last, and every other parameter by its name
     * or its {@code @GraphQLArgument} value.
     *
     * @param sourceType      the binary name of the source type, or {@code null} for a root resolver method
     * @param parameters      the parameters of the method, in declaration order
     * @param argumentNames   the names of the arguments of the field, in schema order
     * @return the verdict
     */
    public static ArgumentVerdict arguments(@Nullable String sourceType, List<JParameter> parameters,
                                            List<String> argumentNames) {
        int required = argumentNames.size() + (sourceType == null ? 0 : 1);
        if (required == 0 && parameters.isEmpty()) {
            return new ArgumentVerdict(List.of(), null);
        }

        int found = (int) parameters.stream().filter(it -> !isEnvironment(it.type())).count();
        if (found != required) {
            return new ArgumentVerdict(List.of(), new CountMismatch(found > required, found, required));
        }

        List<Slot> slots = new ArrayList<>();
        int first = 0;
        int end = parameters.size();
        if (sourceType != null) {
            if (!isType(parameters.get(0).type(), sourceType)) {
                return new ArgumentVerdict(slots, new InvalidSource(0));
            }
            slots.add(new Slot(SlotKind.SOURCE, 0, null));
            first = 1;
        }
        boolean environment = end > first && isEnvironment(parameters.get(end - 1).type());
        if (environment) {
            end--;
        }

        List<String> available = List.copyOf(argumentNames);
        Set<String> bound = new HashSet<>();
        for (int i = first; i < end; i++) {
            JParameter parameter = parameters.get(i);
            String name = parameter.annotations().stringValue(AnnotationNames.ARGUMENT, "value")
                    .orElse(parameter.name());
            if (!available.contains(name)) {
                return new ArgumentVerdict(slots, new ParameterWithoutArgument(i, name, available));
            }
            if (!bound.add(name)) {
                return new ArgumentVerdict(slots, new DuplicateParameter(name));
            }
            slots.add(new Slot(SlotKind.ARGUMENT, i, name));
        }
        if (environment) {
            slots.add(new Slot(SlotKind.ENVIRONMENT, end, null));
        }
        return new ArgumentVerdict(slots, null);
    }

    /**
     * @param methodName      the method name
     * @param objectTypeName  the schema type of the field
     * @param sourceType      the binary name of the source type, or {@code null}
     * @param schemaArguments the arguments of the field
     * @return a signature that fits the field, or {@code null} when the field needs no parameter
     */
    @Nullable
    public static String suggestedMethod(String methodName, String objectTypeName, @Nullable String sourceType,
                                         List<SchemaArgument> schemaArguments) {
        List<String> parameters = new ArrayList<>();
        if (sourceType != null) {
            parameters.add(sourceType + " " + Character.toLowerCase(objectTypeName.charAt(0))
                    + objectTypeName.substring(1));
        }
        schemaArguments.forEach(it -> parameters.add(it.typeName() + " " + it.name()));
        return parameters.isEmpty() ? null : methodName + "(" + String.join(", ", parameters) + ")";
    }

    private static boolean isEnvironment(TypeRef type) {
        return isType(type, DATA_FETCHING_ENVIRONMENT);
    }

    private static boolean isType(TypeRef type, String binaryName) {
        return !type.array() && type.rawName().equals(binaryName);
    }

    /**
     * An argument of a schema field.
     *
     * @param name     the argument name
     * @param typeName the name of the argument type, without wrappers
     */
    public record SchemaArgument(String name, String typeName) {
    }
}
