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
import io.micronaut.graphql.tools.binding.internal.model.JClass;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The input types that are bound to each Java input class, so that a property is unmapped only when no input type of its
 * class names it. Both adapters collect the types here and read the verdict per class.
 */
@Internal
public final class InputClassCoverage {

    private final Map<String, Entry> byClass = new LinkedHashMap<>();

    /**
     * @param inputType the name of a schema input type
     * @param fields    the names of its fields
     * @param inputClass the Java class it is bound to
     */
    public void add(String inputType, Collection<String> fields, JClass inputClass) {
        Entry entry = byClass.computeIfAbsent(inputClass.name(), it -> new Entry(inputClass));
        entry.types.add(inputType);
        entry.fields.addAll(fields);
    }

    /**
     * @param others the input types that were not added, with their fields: another resolver may bind them to any class, so
     *               their fields cover a property as well
     * @return the verdict of each class, in the order the classes were first added
     */
    public List<ClassCoverage> verdicts(Map<String, ? extends Collection<String>> others) {
        List<ClassCoverage> result = new ArrayList<>();
        for (Entry entry : byClass.values()) {
            Set<String> covered = new HashSet<>(entry.fields);
            others.values().forEach(covered::addAll);
            result.add(new ClassCoverage(entry.types.first(), List.copyOf(entry.types), entry.inputClass,
                    InputTypeRules.check(covered, entry.inputClass), !smallerCandidate(entry, others)));
        }
        return result;
    }

    // The startup names a finding after the smallest input type bound to the class. An input type that was not added and sorts
    // before it, with every field settable on the class, may be bound to it by another resolver: the smallest name is unknown.
    private static boolean smallerCandidate(Entry entry, Map<String, ? extends Collection<String>> others) {
        Set<String> settable = new HashSet<>();
        entry.inputClass.writableProperties().forEach(it -> settable.add(it.name()));
        String smallest = entry.types.first();
        return others.entrySet().stream()
                .anyMatch(it -> it.getKey().compareTo(smallest) < 0 && settable.containsAll(it.getValue()));
    }

    /**
     * @return the names of the input types added so far
     */
    public Set<String> inputTypes() {
        Set<String> result = new HashSet<>();
        byClass.values().forEach(it -> result.addAll(it.types));
        return result;
    }

    /**
     * @param inputType the smallest name of the input types bound to the class, which the findings name
     * @param inputTypes every input type bound to the class, sorted
     * @param inputClass the class
     * @param verdict    what no input type of the class covers
     * @param named      whether {@code inputType} is the name the startup gives the findings of the class
     */
    public record ClassCoverage(String inputType, List<String> inputTypes, JClass inputClass, InputVerdict verdict,
                                boolean named) {
    }

    private static final class Entry {

        final JClass inputClass;
        final TreeSet<String> types = new TreeSet<>();
        final Set<String> fields = new HashSet<>();

        Entry(JClass inputClass) {
            this.inputClass = inputClass;
        }
    }
}
