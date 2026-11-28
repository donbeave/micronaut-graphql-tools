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
package io.micronaut.graphql.tools.binding.internal.client.method;

import graphql.language.Directive;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLUnionType;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The fields that a selection selects on a type, with fragment spreads and inline fragments followed. The fields of an object
 * type are merged by response key. The fields of an interface or union are in two parts: the {@code common} fields, which every
 * object type that can occur gets, and the fields that only some object types get, for each of those types, so that a result type
 * can have one branch per object type. A fragment that applies to every possible type, such as one on the interface itself, adds
 * to the common fields.
 */
@Internal
final class ClientSelections {

    private final GraphQLSchema schema;
    private final Map<String, FragmentDefinition> fragments;

    ClientSelections(GraphQLSchema schema, Map<String, FragmentDefinition> fragments) {
        this.schema = schema;
        this.fragments = fragments;
    }

    /**
     * @param sets   the selection sets that select on the type, one per field of the same response key
     * @param parent the type that they select on
     * @return the fields of the selection
     */
    Branches collect(List<SelectionSet> sets, GraphQLType parent) {
        Set<String> all = possibleTypes(parent);
        Branches branches = new Branches();
        for (SelectionSet set : sets) {
            walk(set, parent, null, all, false, new HashSet<>(), branches);
        }
        return branches;
    }

    private void walk(SelectionSet set, GraphQLType scope, @Nullable Set<String> applicable, Set<String> all, boolean conditional,
                      Set<String> spreading, Branches out) {
        for (Selection<?> selection : set.getSelections()) {
            if (selection instanceof Field field) {
                SelectedField selected = new SelectedField(field, scope instanceof GraphQLFieldsContainer container ? container : null,
                        conditional || conditional(field.getDirectives()));
                if (applicable == null) {
                    out.common.computeIfAbsent(field.getResultKey(), key -> new ArrayList<>()).add(selected);
                } else {
                    for (String type : applicable) {
                        out.specific.computeIfAbsent(type, key -> new LinkedHashMap<>())
                                .computeIfAbsent(field.getResultKey(), key -> new ArrayList<>()).add(selected);
                    }
                }
            } else if (selection instanceof InlineFragment inline) {
                GraphQLType type = inline.getTypeCondition() == null ? scope : schema.getType(inline.getTypeCondition().getName());
                if (type != null) {
                    walk(inline.getSelectionSet(), type, narrow(applicable, type, all), all,
                            conditional || conditional(inline.getDirectives()), spreading, out);
                }
            } else if (selection instanceof FragmentSpread spread && spreading.add(spread.getName())) {
                FragmentDefinition definition = fragments.get(spread.getName());
                GraphQLType type = definition == null ? null : schema.getType(definition.getTypeCondition().getName());
                if (type != null) {
                    walk(definition.getSelectionSet(), type, narrow(applicable, type, all), all,
                            conditional || conditional(spread.getDirectives()), spreading, out);
                }
                spreading.remove(spread.getName());
            }
        }
    }

    // the object types that a fragment on the type applies to, null when it applies to every possible type
    private @Nullable Set<String> narrow(@Nullable Set<String> applicable, GraphQLType type, Set<String> all) {
        Set<String> result = new LinkedHashSet<>(applicable == null ? all : applicable);
        result.retainAll(possibleTypes(type));
        return result.equals(all) ? null : result;
    }

    /**
     * @param name the name of a type
     * @return the type of the schema, null when it has none
     */
    @Nullable GraphQLType type(String name) {
        return schema.getType(name);
    }

    private Set<String> possibleTypes(GraphQLType type) {
        Set<String> names = new LinkedHashSet<>();
        if (type instanceof GraphQLObjectType object) {
            names.add(object.getName());
        } else if (type instanceof GraphQLInterfaceType itf) {
            schema.getImplementations(itf).forEach(it -> names.add(it.getName()));
        } else if (type instanceof GraphQLUnionType union) {
            union.getTypes().forEach(it -> names.add(it.getName()));
        }
        return names;
    }

    // a field that a directive may leave out of the response
    private static boolean conditional(List<Directive> directives) {
        return directives.stream().anyMatch(it -> it.getName().equals("skip") || it.getName().equals("include"));
    }

    /**
     * One occurrence of a field in a selection.
     *
     * @param field       the field node
     * @param scope       the type that declares the field, null when it has none ({@code __typename} of a union)
     * @param conditional whether a {@code @skip} or {@code @include} on the field or on a fragment around it may leave it out
     */
    record SelectedField(Field field, @Nullable GraphQLFieldsContainer scope, boolean conditional) {
    }

    /**
     * What a selection selects on an object, interface or union type.
     */
    static final class Branches {

        final Map<String, List<SelectedField>> common = new LinkedHashMap<>();
        final Map<String, Map<String, List<SelectedField>>> specific = new LinkedHashMap<>();

        /**
         * @param type the name of an object type
         * @return the fields that an object of the type gets: the common ones and those of its own branch
         */
        Map<String, List<SelectedField>> forType(String type) {
            Map<String, List<SelectedField>> merged = new LinkedHashMap<>();
            common.forEach((key, fields) -> merged.put(key, new ArrayList<>(fields)));
            specific.getOrDefault(type, Map.of()).forEach((key, fields) -> merged.computeIfAbsent(key, k -> new ArrayList<>()).addAll(fields));
            return merged;
        }
    }
}
