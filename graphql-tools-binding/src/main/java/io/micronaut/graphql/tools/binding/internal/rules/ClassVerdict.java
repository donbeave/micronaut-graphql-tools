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

import java.util.List;

/**
 * The outcome of the per-class rules for one resolver class: for every method that can bind a field, the coordinates it
 * binds and, for each, the argument plan and the return adaptation. A violation is carried by the verdicts of the field
 * ({@link ArgumentVerdict#failure()}, {@link ReturnVerdict#failure()}); the code registry decides how it is reported.
 *
 * @param type    the binary name of the resolver class
 * @param methods the methods that can bind a field, in declaration order
 */
@Internal
public record ClassVerdict(String type, List<MethodVerdict> methods) {

    public ClassVerdict {
        methods = List.copyOf(methods);
    }

    /**
     * @param signature   the method as messages print it
     * @param coordinates the coordinates the method can bind, which may be empty: a method that binds no field of
     *                    the root types, as in {@link ResolverClassRules#bindsField}
     * @param fields      the verdicts of the coordinates that name a field of the schema
     */
    public record MethodVerdict(String signature, List<String> coordinates, List<FieldVerdict> fields) {

        public MethodVerdict {
            coordinates = List.copyOf(coordinates);
            fields = List.copyOf(fields);
        }
    }

    /**
     * @param coordinate the coordinate {@code Type.field}
     * @param arguments  how the parameters bind the arguments of the field
     * @param returns    the return type against the type of the field, with the verdict for each list level
     */
    public record FieldVerdict(String coordinate, ArgumentVerdict arguments, ReturnVerdict returns) {
    }
}
