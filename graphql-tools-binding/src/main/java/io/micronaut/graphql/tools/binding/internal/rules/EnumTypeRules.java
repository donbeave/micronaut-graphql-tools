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
import io.micronaut.graphql.tools.binding.internal.model.JEnumConstant;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The rule body for a Java enum that is bound to a schema enum. Both adapters call it.
 */
@Internal
public final class EnumTypeRules {

    private EnumTypeRules() {
    }

    /**
     * Compares the values of a schema enum with the constants of a Java enum.
     *
     * @param schemaValues the names of the schema enum values
     * @param enumClass    the Java enum
     * @return the verdict
     */
    public static EnumVerdict check(Collection<String> schemaValues, JClass enumClass) {
        Set<String> expected = new TreeSet<>(schemaValues);
        Map<String, JEnumConstant> constants = new TreeMap<>();
        for (JEnumConstant constant : enumClass.enumConstants()) {
            constants.put(constant.name(), constant);
        }

        List<String> missingSchemaValues = expected.stream().filter(it -> !constants.containsKey(it)).toList();
        List<String> unbound = new ArrayList<>();
        List<String> ignored = new ArrayList<>();
        for (JEnumConstant constant : constants.values()) {
            if (expected.contains(constant.name())) {
                continue;
            }
            // the annotations are read for a constant without a schema value only
            if (constant.annotations().has(AnnotationNames.IGNORE)) {
                ignored.add(constant.name());
            } else {
                unbound.add(constant.name());
            }
        }
        return new EnumVerdict(missingSchemaValues, unbound, ignored);
    }
}
