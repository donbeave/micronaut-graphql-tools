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
package io.micronaut.graphql.tools.binding.internal.client;

import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.InlineFragment;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.GraphQLUnionType;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Puts {@code __typename} first in every selection set of an abstract type, an interface or a union, that does not select it
 * already. The type name is what tells the client which object type of the response a value is, so a result type that is one of
 * several cannot be decoded without it, and a document that omits it is completed here, once, at compile time. The selection
 * sets of the operation and of the fragments it uses are walked with the types of the schema: the type of a field, of an inline
 * fragment and of the type condition of a fragment definition. The nodes of the document are never changed, the walk builds
 * copies.
 */
@Internal
public final class ClientTypenames {

    /** The name of the field. */
    public static final String TYPENAME = "__typename";

    private ClientTypenames() {
    }

    /**
     * @param schema    the client schema
     * @param operation the operation
     * @param fragments the fragments that the operation uses, by name
     * @return the operation and the fragments, each with the type name selected in its abstract selection sets
     */
    public static Injected inject(GraphQLSchema schema, OperationDefinition operation, Map<String, FragmentDefinition> fragments) {
        GraphQLType root = switch (operation.getOperation()) {
            case QUERY -> schema.getQueryType();
            case MUTATION -> schema.getMutationType();
            case SUBSCRIPTION -> schema.getSubscriptionType();
        };
        OperationDefinition changed = operation;
        if (root != null && operation.getSelectionSet() != null) {
            changed = operation.transform(builder -> builder.selectionSet(selections(schema, operation.getSelectionSet(), root)));
        }
        Map<String, FragmentDefinition> changedFragments = new TreeMap<>();
        for (Map.Entry<String, FragmentDefinition> entry : fragments.entrySet()) {
            FragmentDefinition fragment = entry.getValue();
            GraphQLType type = schema.getType(fragment.getTypeCondition().getName());
            changedFragments.put(entry.getKey(), type == null ? fragment
                    : fragment.transform(builder -> builder.selectionSet(selections(schema, fragment.getSelectionSet(), type))));
        }
        return new Injected(changed, changedFragments);
    }

    private static SelectionSet selections(GraphQLSchema schema, SelectionSet set, GraphQLType parent) {
        List<Selection> result = new ArrayList<>();
        boolean selected = false;
        for (Selection<?> selection : set.getSelections()) {
            if (selection instanceof Field field) {
                selected |= isTypename(field);
                result.add(inside(schema, field, parent));
            } else if (selection instanceof InlineFragment fragment) {
                GraphQLType type = fragment.getTypeCondition() == null ? parent : schema.getType(fragment.getTypeCondition().getName());
                result.add(type == null ? fragment : fragment.transform(builder -> builder.selectionSet(selections(schema,
                        fragment.getSelectionSet(), type))));
            } else {
                result.add(selection);
            }
        }
        if (!selected && isAbstract(parent)) {
            result.add(0, Field.newField(TYPENAME).build());
        }
        return set.transform(builder -> builder.selections(result));
    }

    private static Field inside(GraphQLSchema schema, Field field, GraphQLType parent) {
        if (field.getSelectionSet() == null || !(parent instanceof GraphQLFieldsContainer container)) {
            return field;
        }
        GraphQLFieldDefinition definition = container.getFieldDefinition(field.getName());
        if (definition == null) {
            return field;
        }
        GraphQLType child = GraphQLTypeUtil.unwrapAll(definition.getType());
        return field.transform(builder -> builder.selectionSet(selections(schema, field.getSelectionSet(), child)));
    }

    private static boolean isTypename(Field field) {
        return TYPENAME.equals(field.getName()) && field.getAlias() == null && field.getDirectives().isEmpty();
    }

    private static boolean isAbstract(@Nullable GraphQLType type) {
        return type instanceof GraphQLInterfaceType || type instanceof GraphQLUnionType;
    }

    /**
     * The operation and the fragments after the injection.
     *
     * @param operation the operation
     * @param fragments the fragments, by name
     */
    public record Injected(OperationDefinition operation, Map<String, FragmentDefinition> fragments) {
    }
}
