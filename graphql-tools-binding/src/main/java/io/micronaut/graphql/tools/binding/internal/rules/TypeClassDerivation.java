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

import graphql.language.ObjectTypeDefinition;
import graphql.language.Type;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.binding.internal.finding.Finding;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The rule that derives the Java class of an object type that a union or an interface needs and the dictionary does not
 * name: a class that declares the type with {@code @GraphQLType(name)}, or, where a naming convention is set, a class
 * whose simple name is the convention with the type name in it, or, for a type that a sealed Java type declares, the
 * permitted subclass of that name. The startup and the compile-time checks apply it, so that
 * they reach one verdict.
 */
@Internal
public final class TypeClassDerivation {

    /** The placeholder of the naming convention that stands for the name of the object type. */
    public static final String PLACEHOLDER = "{Type}";

    private TypeClassDerivation() {
    }

    /**
     * @param convention the naming convention, or {@code null} or blank for none
     * @return why the convention is not valid, or {@code null} if it is valid or absent
     */
    @Nullable
    public static String validate(@Nullable String convention) {
        if (convention == null || convention.isBlank()) {
            return null;
        }
        int first = convention.indexOf(PLACEHOLDER);
        if (first < 0 || first != convention.lastIndexOf(PLACEHOLDER)) {
            return "the naming convention '" + convention + "' must contain " + PLACEHOLDER + " exactly once, for the name of the object type";
        }
        return null;
    }

    /**
     * @param typeName   the name of an object type
     * @param convention the naming convention, or {@code null} or blank for none
     * @param candidates the classes that can stand for a type
     * @return the binary names of the classes that match the type, sorted
     */
    public static List<String> matching(String typeName, @Nullable String convention, Collection<Candidate> candidates) {
        String conventional = convention == null || convention.isBlank() ? null : convention.strip().replace(PLACEHOLDER, typeName);
        return candidates.stream()
                .filter(it -> it.declaredName().isEmpty() ? it.simpleName().equals(conventional) : it.declaredName().equals(typeName))
                .map(Candidate::className)
                .sorted()
                .toList();
    }

    /**
     * The match inside the permitted subclasses of a sealed type, where the type needs no naming convention: a class that
     * declares the name with {@code @GraphQLType(name)}, or, where it declares none, whose simple name is the type name.
     *
     * @param typeName   the name of an object type
     * @param candidates the permitted subclasses that can stand for a type
     * @return the binary names of the classes that match the type, sorted
     */
    public static List<String> matchingPermitted(String typeName, Collection<Candidate> candidates) {
        return candidates.stream()
                .filter(it -> it.declaredName().isEmpty() ? it.simpleName().equals(typeName) : it.declaredName().equals(typeName))
                .map(Candidate::className)
                .sorted()
                .toList();
    }

    /**
     * @param registry the schema
     * @return the names of the object types that need a class by derivation: the members of a union and the object types
     * that implement an interface
     */
    public static Set<String> neededTypeNames(TypeDefinitionRegistry registry) {
        Set<String> names = new TreeSet<>();
        for (UnionTypeDefinition union : registry.getTypes(UnionTypeDefinition.class)) {
            for (Type<?> member : union.getMemberTypes()) {
                if (member instanceof TypeName name
                        && registry.getType(name.getName()).orElse(null) instanceof ObjectTypeDefinition) {
                    names.add(name.getName());
                }
            }
        }
        for (ObjectTypeDefinition object : registry.getTypes(ObjectTypeDefinition.class)) {
            if (!object.getImplements().isEmpty()) {
                names.add(object.getName());
            }
        }
        return names;
    }

    /**
     * @param typeName the object type
     * @param classes  the binary names of the classes that match it, at least two
     * @return the finding
     */
    public static Finding ambiguity(String typeName, List<String> classes) {
        return new Finding(BindingCode.AMBIGUOUS_TYPE_CLASS.getCode(), typeName,
                "The object type " + typeName + " matches several classes: " + String.join(", ", classes) + ".",
                null, null, null,
                "declare the class with @GraphQLType(name = \"" + typeName + "\") on one class only, rename the others, or map "
                        + typeName + " in the SchemaMappingDictionary, which always wins");
    }

    /**
     * @param registry   the schema
     * @param convention the naming convention, or {@code null}
     * @param candidates the classes that can stand for a type
     * @return one finding for each needed object type that matches several classes, in the order of the type names
     */
    public static List<Finding> ambiguities(TypeDefinitionRegistry registry, @Nullable String convention,
                                            Collection<Candidate> candidates) {
        List<Finding> findings = new ArrayList<>();
        for (String name : neededTypeNames(registry)) {
            List<String> classes = matching(name, convention, candidates);
            if (classes.size() > 1) {
                findings.add(ambiguity(name, classes));
            }
        }
        return findings;
    }

    /**
     * One class that can stand for an object type.
     *
     * @param className     the binary name of the class
     * @param simpleName    its simple name
     * @param declaredName  the value of {@code @GraphQLType(name)}, or an empty string
     */
    public record Candidate(String className, String simpleName, String declaredName) {
    }
}
