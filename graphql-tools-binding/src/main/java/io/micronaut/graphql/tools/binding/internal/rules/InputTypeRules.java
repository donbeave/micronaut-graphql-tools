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
import io.micronaut.graphql.tools.binding.internal.model.JClass;
import io.micronaut.graphql.tools.binding.internal.model.JProperty;
import io.micronaut.graphql.tools.binding.internal.model.TypeRef;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The rule body for a Java class that is bound to a schema input type. Both adapters call it.
 */
@Internal
public final class InputTypeRules {

    /** The binary name of the wrapper that tells an omitted value from a null. */
    public static final String ARGUMENT_VALUE = "io.micronaut.graphql.tools.input.ArgumentValue";

    // the declared types a list argument may have: the collections that the startup instantiates (an ArrayList, a
    // LinkedHashSet, a TreeSet or a LinkedList) and every type that those collections have as a supertype
    private static final Set<String> SUPPORTED_LISTS = Set.of("java.lang.Iterable", "java.util.Collection",
            "java.util.List", "java.util.Set", "java.util.SortedSet", "java.util.NavigableSet", "java.util.Queue",
            "java.util.Deque", "java.util.SequencedCollection", "java.util.SequencedSet", "java.util.AbstractCollection",
            "java.util.AbstractList", "java.util.AbstractSequentialList", "java.util.AbstractSet",
            "java.util.ArrayList", "java.util.LinkedList", "java.util.HashSet", "java.util.LinkedHashSet",
            "java.util.TreeSet");

    private InputTypeRules() {
    }

    /**
     * @param inputClass a Java class bound to a schema input type
     * @return its properties
     */
    public static InputFields fields(JClass inputClass) {
        return new InputFields(inputClass);
    }

    /**
     * The decisions about the Java class of an input object, in the order the startup takes them: an interface, the
     * introspection, an enum.
     *
     * @param inputClass a Java class bound to a schema input type
     * @return the problem, or {@code null} when the fields can be read
     */
    @Nullable
    public static ClassProblem classProblem(JClass inputClass) {
        if (inputClass.kind() == JClass.Kind.INTERFACE) {
            return ClassProblem.NOT_A_CLASS;
        }
        if (!inputClass.introspected()) {
            return ClassProblem.NOT_INTROSPECTED;
        }
        return inputClass.kind() == JClass.Kind.ENUM ? ClassProblem.NOT_A_CLASS : null;
    }

    /**
     * @param binaryName the declared collection type of a list argument or input field
     * @return whether a list of the schema can be converted into it
     */
    public static boolean supportedList(String binaryName) {
        return SUPPORTED_LISTS.contains(binaryName);
    }

    /**
     * @param binaryName the declared collection type
     * @return the reason that the type is not supported for a list
     */
    public static String unsupportedListReason(String binaryName) {
        return "a list is converted into a List, a Collection, an Iterable, a Set, a SortedSet, a Deque or a Queue, "
                + "but the declared type is " + binaryName + ".";
    }

    /**
     * Whether an {@code ArgumentValue} is declared where it can never work.
     *
     * @param declared   the declared type
     * @param fieldLevel whether the type is the type of the argument or input field itself, not a list element
     * @param nonNull    whether the schema type is non-null
     * @return the misuse, or {@code null}
     */
    @Nullable
    public static ArgumentValueMisuse argumentValueMisuse(TypeRef declared, boolean fieldLevel, boolean nonNull) {
        if (!fieldLevel) {
            return ArgumentValueMisuse.IN_LIST;
        }
        if (nonNull) {
            return ArgumentValueMisuse.AT_NON_NULL;
        }
        if (declared.typeArguments().isEmpty()) {
            return ArgumentValueMisuse.NO_TYPE_ARGUMENT;
        }
        return ARGUMENT_VALUE.equals(declared.typeArguments().get(0).rawName()) ? ArgumentValueMisuse.NESTED : null;
    }

    /**
     * @param misuse      the misuse
     * @param printedType the schema type as it is written in the schema
     * @return the reason
     */
    public static String argumentValueReason(ArgumentValueMisuse misuse, String printedType) {
        return switch (misuse) {
            case IN_LIST -> ARGUMENT_VALUE + " is not allowed inside a list: a list element cannot be omitted.";
            case AT_NON_NULL -> ARGUMENT_VALUE + " is only allowed for a nullable GraphQL type, the type is "
                    + printedType + ".";
            case NO_TYPE_ARGUMENT -> ARGUMENT_VALUE + " must declare the type it wraps.";
            case NESTED -> ARGUMENT_VALUE + " cannot wrap " + ARGUMENT_VALUE + ".";
        };
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

    /** What is wrong with the Java class of an input object before its fields are read. */
    public enum ClassProblem {
        /** An interface or an enum: the mapping is wrong, which only the startup reports. */
        NOT_A_CLASS,
        /** The class has no bean introspection. */
        NOT_INTROSPECTED
    }

    /** What an input field of the schema finds in the Java class. */
    public enum FieldBinding {
        /** The class has no property of that name: the mapping is wrong, which only the startup reports. */
        MISSING,
        /** The property is read-only and no constructor parameter sets it. */
        NOT_SETTABLE,
        /** The property can be set. */
        SETTABLE
    }

    /** The ways to declare an {@code ArgumentValue} that fail whatever the configuration is. */
    public enum ArgumentValueMisuse {
        IN_LIST, AT_NON_NULL, NO_TYPE_ARGUMENT, NESTED
    }

    /**
     * The properties of a Java input class, read once, for the fields of the input type that it is bound to.
     */
    public static final class InputFields {

        private final Set<String> existing = new HashSet<>();
        private final Set<String> settable = new HashSet<>();

        private InputFields(JClass inputClass) {
            for (JProperty property : inputClass.readableProperties()) {
                existing.add(property.name());
            }
            for (JProperty property : inputClass.writableProperties()) {
                existing.add(property.name());
                settable.add(property.name());
            }
        }

        /**
         * @param field the name of an input field
         * @return what the class has for it
         */
        public FieldBinding binding(String field) {
            if (!existing.contains(field)) {
                return FieldBinding.MISSING;
            }
            return settable.contains(field) ? FieldBinding.SETTABLE : FieldBinding.NOT_SETTABLE;
        }
    }
}
