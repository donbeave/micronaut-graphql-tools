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
import io.micronaut.graphql.tools.binding.internal.model.AnnotationNames;
import io.micronaut.graphql.tools.binding.internal.model.JClass;
import io.micronaut.graphql.tools.binding.internal.model.JProperty;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The rule body for a Java class that is bound to a schema input type. Both adapters call it.
 */
@Internal
public final class InputTypeRules {

    private InputTypeRules() {
    }

    /**
     * Finds the settable properties of a Java input class that no field of the schema input type names.
     * {@code @GraphQLIgnore} excludes a property, unless a field names it anyway.
     *
     * @param inputFields the names of the fields of the schema input type
     * @param inputClass  the Java class
     * @return the verdict
     */
    public static InputVerdict check(Collection<String> inputFields, JClass inputClass) {
        Set<String> fields = Set.copyOf(inputFields);
        Set<String> unmapped = new TreeSet<>();
        Set<String> ignored = new TreeSet<>();
        for (JProperty property : inputClass.writableProperties()) {
            if (fields.contains(property.name())) {
                continue;
            }
            if (property.annotations().has(AnnotationNames.IGNORE)) {
                ignored.add(inputClass.name() + "#" + property.name());
            } else {
                unmapped.add(property.name());
            }
        }
        return new InputVerdict(List.copyOf(unmapped), List.copyOf(ignored));
    }
}
