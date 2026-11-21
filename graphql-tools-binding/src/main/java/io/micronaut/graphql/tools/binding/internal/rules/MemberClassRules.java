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

import graphql.language.FieldDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.Type;
import graphql.language.TypeName;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.binding.internal.finding.Finding;
import io.micronaut.graphql.tools.binding.internal.finding.SchemaLocation;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaModel;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The rule that an object type of a union or interface is not mapped to a Java class that is disjoint from the Java type
 * the field declares for the union or interface ({@code incorrect-member-mapping}). The startup applies it to the class that
 * the dictionary, {@code @GraphQLType(name)} or the naming convention names. The compile-time check knows only the last
 * two, so it applies the rule to the classes of its own compilation that match by name.
 *
 * <p>The startup binds the members of a union in the order of the schema and stops at the first one that fails, and it
 * binds a union once, from the first field that reaches it. The rule reports the first member that is disjoint, and only
 * when every member before it has a class that is known here, and only for a union or interface that exactly one field of
 * the schema returns, so that the field is the one the startup reaches it from.</p>
 */
@Internal
public final class MemberClassRules {

    private MemberClassRules() {
    }

    /**
     * @param schema       the schema
     * @param abstractName the name of a union or interface
     * @return how many fields of the object and interface types return it, whatever the wrappers and lists
     */
    public static long fieldsReturning(SchemaModel schema, String abstractName) {
        return schema.registry().types().values().stream()
                .flatMap(it -> it instanceof ObjectTypeDefinition object ? object.getFieldDefinitions().stream()
                        : it instanceof InterfaceTypeDefinition iface ? iface.getFieldDefinitions().stream()
                        : java.util.stream.Stream.<FieldDefinition>empty())
                .filter(it -> abstractName.equals(baseName(it.getType())))
                .count();
    }

    private static String baseName(Type<?> type) {
        if (type instanceof NonNullType nonNull) {
            return baseName(nonNull.getType());
        }
        if (type instanceof ListType list) {
            return baseName(list.getType());
        }
        return type instanceof TypeName name ? name.getName() : "";
    }

    /**
     * @param returned the field
     * @param classes  the class of an object type, or empty where it is not known here: no class of the compilation matches
     *                 it, or several do
     * @return the finding for the first object type that is disjoint from the declared Java type, empty when there is none
     * or an object type before it has no known class
     */
    public static Optional<Finding> check(AbstractReturn returned, Function<String, Optional<MemberClass>> classes) {
        for (String objectType : returned.memberTypes()) {
            MemberClass member = classes.apply(objectType).orElse(null);
            if (member == null) {
                return Optional.empty();
            }
            if (disjoint(returned.declaredClass(), member)) {
                return Optional.of(finding(returned, objectType, member));
            }
        }
        return Optional.empty();
    }

    // the declared type is an interface: a class is disjoint from it when it is final and does not implement it
    private static boolean disjoint(String declared, MemberClass member) {
        return member.finalClass() && !member.name().equals(declared) && !member.supertypes().contains(declared);
    }

    private static Finding finding(AbstractReturn returned, String objectType, MemberClass member) {
        String kind = returned.union() ? "union" : "interface";
        String sentence = "The " + kind + " " + returned.abstractName() + " is returned as " + returned.declaredClass()
                + ", but its object type " + objectType + " is mapped to " + member.name() + ", and no object can be an "
                + "instance of both " + returned.declaredClass() + " and " + member.name() + ", so no object the field "
                + "returns can resolve to " + objectType + ".";
        if (returned.inheritedBy() != null) {
            sentence += " (inherited by " + returned.inheritedBy() + ")";
        }
        return new Finding(BindingCode.INCORRECT_MEMBER_MAPPING.code(), returned.coordinate(), sentence, returned.member(),
                null, returned.location(), "map " + objectType + " to a Java type that " + returned.declaredClass()
                + " can implement or extend, or make " + member.name() + " implement it");
    }

    /**
     * A field that returns a union or an interface as a Java interface.
     *
     * @param member        the Java member that returns it, as {@code binary.Name#member(parameter types)}
     * @param coordinate    the coordinate of the field
     * @param location      where the schema defines the field
     * @param union         whether the abstract type is a union, else an interface
     * @param abstractName  the name of the union or interface
     * @param declaredClass the binary name of the Java interface the field declares for it, after the wrappers and lists
     * @param memberTypes   the names of its object types in the order the startup binds them
     * @param inheritedBy   the binary name of the resolver class that inherits the member, or {@code null}
     */
    public record AbstractReturn(String member, String coordinate, @Nullable SchemaLocation location, boolean union,
                                 String abstractName, String declaredClass, List<String> memberTypes,
                                 @Nullable String inheritedBy) {

        public AbstractReturn {
            memberTypes = List.copyOf(memberTypes);
        }
    }

    /**
     * A class of the compilation that can stand for an object type.
     *
     * @param name       the binary name
     * @param finalClass whether the class is final
     * @param supertypes the binary names of all its supertypes
     */
    public record MemberClass(String name, boolean finalClass, Set<String> supertypes) {

        public MemberClass {
            supertypes = Set.copyOf(supertypes);
        }
    }
}
