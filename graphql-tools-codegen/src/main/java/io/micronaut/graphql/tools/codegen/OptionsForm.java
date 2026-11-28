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
package io.micronaut.graphql.tools.codegen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * The string form of {@link Options}: the one place that knows the key of each component.
 */
final class OptionsForm {

    private static final String SIZE = ".size";
    private static final String TYPE_MAPPINGS = "typeMappings.";

    private OptionsForm() {
    }

    static Map<String, String> toMap(Options o) {
        Options defaults = Options.builder().build();
        Map<String, String> map = new TreeMap<>();
        put(map, "language", o.language(), defaults.language());
        put(map, "mode", o.mode(), defaults.mode());
        put(map, "packageName", o.packageName(), defaults.packageName());
        put(map, "modelPackage", o.modelPackage(), defaults.modelPackage());
        put(map, "resolverPackage", o.resolverPackage(), defaults.resolverPackage());
        put(map, "namesPackage", o.namesPackage(), defaults.namesPackage());
        put(map, "generatedAnnotation", o.generatedAnnotation(), defaults.generatedAnnotation());
        put(map, "modelStyle", o.modelStyle(), defaults.modelStyle());
        putList(map, "interfaceTypes", o.interfaceTypes(), defaults.interfaceTypes());
        put(map, "sealedAbstractTypes", o.sealedAbstractTypes(), defaults.sealedAbstractTypes());
        put(map, "typeNamePrefix", o.typeNamePrefix(), defaults.typeNamePrefix());
        put(map, "typeNameSuffix", o.typeNameSuffix(), defaults.typeNameSuffix());
        put(map, "nullableAnnotation", o.nullableAnnotation(), Options.defaultNullableAnnotation(o.mode()));
        put(map, "rootContracts", o.rootContracts(), defaults.rootContracts());
        put(map, "contractNameTemplate", o.contractNameTemplate(), defaults.contractNameTemplate());
        put(map, "kotlinSuspend", o.kotlinSuspend(), defaults.kotlinSuspend());
        put(map, "subscriptionReturnType", o.subscriptionReturnType(), defaults.subscriptionReturnType());
        o.typeMappings().forEach((key, value) -> map.put(TYPE_MAPPINGS + key, value));
        put(map, "typeMappingsFromDependencies", o.typeMappingsFromDependencies(), defaults.typeMappingsFromDependencies());
        put(map, "nameConstants", o.nameConstants(), defaults.nameConstants());
        putList(map, "includes", o.includes(), defaults.includes());
        putList(map, "excludes", o.excludes(), defaults.excludes());
        put(map, "commentsAsDescriptions", o.commentsAsDescriptions(), defaults.commentsAsDescriptions());
        return Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    private static void put(Map<String, String> map, String name, Object value, Object defaultValue) {
        if (!value.equals(defaultValue)) {
            map.put(name, value instanceof Enum<?> e ? e.name() : value.toString());
        }
    }

    private static void putList(Map<String, String> map, String name, List<String> value, List<String> defaultValue) {
        if (value.equals(defaultValue)) {
            return;
        }
        map.put(name + SIZE, Integer.toString(value.size()));
        for (int i = 0; i < value.size(); i++) {
            map.put(name + "." + i, value.get(i));
        }
    }

    static OptionsResult fromMap(Map<String, String> map) {
        Reader reader = new Reader(map);
        Options.Builder b = Options.builder();
        reader.enumValue("mode", Mode.values(), b::mode);
        reader.enumValue("language", Language.values(), b::language);
        reader.string("packageName", b::packageName);
        reader.string("modelPackage", b::modelPackage);
        reader.string("resolverPackage", b::resolverPackage);
        reader.string("namesPackage", b::namesPackage);
        reader.string("generatedAnnotation", b::generatedAnnotation);
        reader.enumValue("modelStyle", ModelStyle.values(), b::modelStyle);
        reader.list("interfaceTypes", b::interfaceTypes);
        reader.enumValue("sealedAbstractTypes", SealedAbstractTypes.values(), b::sealedAbstractTypes);
        reader.string("typeNamePrefix", b::typeNamePrefix);
        reader.string("typeNameSuffix", b::typeNameSuffix);
        reader.string("nullableAnnotation", b::nullableAnnotation);
        reader.bool("rootContracts", b::rootContracts);
        reader.string("contractNameTemplate", b::contractNameTemplate);
        reader.bool("kotlinSuspend", b::kotlinSuspend);
        reader.string("subscriptionReturnType", b::subscriptionReturnType);
        reader.typeMappings(b::typeMappings);
        reader.bool("typeMappingsFromDependencies", b::typeMappingsFromDependencies);
        reader.enumValue("nameConstants", NameConstants.values(), b::nameConstants);
        reader.list("includes", b::includes);
        reader.list("excludes", b::excludes);
        reader.bool("commentsAsDescriptions", b::commentsAsDescriptions);
        reader.rejectUnknown();
        return new OptionsResult(b.build(), reader.diagnostics);
    }

    private static final class Reader {

        private final Map<String, String> map;
        private final Set<String> known = new HashSet<>();
        private final List<Diagnostic> diagnostics = new ArrayList<>();

        Reader(Map<String, String> source) {
            this.map = new TreeMap<>();
            source.forEach((key, value) -> {
                if (key != null) {
                    map.put(key, value);
                }
            });
        }

        private String take(String key) {
            known.add(key);
            if (!map.containsKey(key)) {
                return null;
            }
            String value = map.get(key);
            if (value == null) {
                invalid(key, "The option " + key + " has no value.");
            }
            return value;
        }

        private void invalid(String name, String message) {
            diagnostics.add(Diagnostic.optionError(Diagnostic.INVALID_OPTION_VALUE, name, message));
        }

        void string(String name, Consumer<String> sink) {
            String value = take(name);
            if (value != null) {
                sink.accept(value);
            }
        }

        void bool(String name, Consumer<Boolean> sink) {
            String value = take(name);
            if (value == null) {
                return;
            }
            if (value.equals("true") || value.equals("false")) {
                sink.accept(Boolean.parseBoolean(value));
            } else {
                invalid(name, "The option " + name + " must be true or false, not '" + value + "'.");
            }
        }

        <E extends Enum<E>> void enumValue(String name, E[] values, Consumer<E> sink) {
            String value = take(name);
            if (value == null) {
                return;
            }
            for (E candidate : values) {
                if (candidate.name().equals(value)) {
                    sink.accept(candidate);
                    return;
                }
            }
            invalid(name, "The option " + name + " must be one of " + Arrays.stream(values).map(Enum::name).toList()
                    + ", not '" + value + "'.");
        }

        void list(String name, Consumer<List<String>> sink) {
            String sizeKey = name + SIZE;
            String sizeText = take(sizeKey);
            if (sizeText == null) {
                for (String key : map.keySet()) {
                    if (key.startsWith(name + ".") && isIndex(key.substring(name.length() + 1))) {
                        known.add(key);
                        diagnostics.add(Diagnostic.optionError(Diagnostic.MISSING_OPTION, sizeKey,
                                "The option " + key + " needs " + sizeKey + ", the number of elements of " + name + "."));
                    }
                }
                return;
            }
            int size;
            try {
                size = Integer.parseInt(sizeText);
            } catch (NumberFormatException e) {
                size = -1;
            }
            if (size < 0) {
                invalid(sizeKey, "The option " + sizeKey + " must be a number that is not negative, not '" + sizeText + "'.");
                return;
            }
            List<String> elements = new ArrayList<>();
            boolean complete = true;
            for (int i = 0; i < size; i++) {
                String key = name + "." + i;
                String element = take(key);
                if (element == null) {
                    complete = false;
                    if (!map.containsKey(key)) {
                        diagnostics.add(Diagnostic.optionError(Diagnostic.MISSING_OPTION, key,
                                "The option " + key + " is missing: " + sizeKey + " is " + size + "."));
                    }
                } else {
                    elements.add(element);
                }
            }
            if (complete) {
                sink.accept(elements);
            }
        }

        void typeMappings(Consumer<Map<String, String>> sink) {
            Map<String, String> mappings = new TreeMap<>();
            for (Map.Entry<String, String> entry : map.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith(TYPE_MAPPINGS)) {
                    known.add(key);
                    if (entry.getValue() == null) {
                        invalid(key, "The option " + key + " has no value.");
                    } else {
                        mappings.put(key.substring(TYPE_MAPPINGS.length()), entry.getValue());
                    }
                }
            }
            sink.accept(mappings);
        }

        void rejectUnknown() {
            map.keySet().stream().filter(key -> !known.contains(key)).forEach(key ->
                    diagnostics.add(Diagnostic.optionError(Diagnostic.UNKNOWN_OPTION, key,
                            "The key " + key + " is not an option of the code generator.")));
        }

        private static boolean isIndex(String text) {
            return !text.isEmpty() && text.chars().allMatch(Character::isDigit);
        }
    }
}
