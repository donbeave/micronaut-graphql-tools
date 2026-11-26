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
package io.micronaut.graphql.tools.security.internal;

import graphql.ExecutionResult;
import graphql.execution.ExecutionContext;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentationContext;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.security.GraphQLSecurity;
import io.micronaut.graphql.tools.security.GraphQLRootFieldsHook;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Calls the {@link GraphQLRootFieldsHook} beans once for each operation, after the document is parsed and validated and before the
 * first field is fetched. A bean exists only when the application has a hook, so the executions of an application without one
 * have no extra instrumentation.
 */
@Internal
@Singleton
@Requires(beans = GraphQLRootFieldsHook.class)
@Requires(property = GraphQLSecurity.PREFIX + ".enabled", value = "true", defaultValue = "true")
final class RootFieldsInstrumentation extends SimplePerformantInstrumentation {

    private final GraphQLRootFieldsHook[] hooks;

    RootFieldsInstrumentation(List<GraphQLRootFieldsHook> hooks) {
        this.hooks = hooks.toArray(new GraphQLRootFieldsHook[0]);
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginExecuteOperation(InstrumentationExecuteOperationParameters parameters,
                                                                         InstrumentationState state) {
        ExecutionContext execution = parameters.getExecutionContext();
        OperationDefinition operation = execution.getOperationDefinition();
        Set<String> names = new LinkedHashSet<>();
        collect(operation.getSelectionSet(), execution, names, new LinkedHashSet<>());
        List<String> rootFields = List.copyOf(new ArrayList<>(names));
        for (GraphQLRootFieldsHook hook : hooks) {
            hook.onRootFields(operation.getOperation(), rootFields, execution.getGraphQLContext());
        }
        return SimpleInstrumentationContext.noOp();
    }

    private static void collect(SelectionSet selectionSet, ExecutionContext execution, Set<String> names, Set<String> visited) {
        if (selectionSet == null) {
            return;
        }
        for (Selection<?> selection : selectionSet.getSelections()) {
            if (selection instanceof Field field) {
                names.add(field.getName());
            } else if (selection instanceof InlineFragment fragment) {
                collect(fragment.getSelectionSet(), execution, names, visited);
            } else if (selection instanceof FragmentSpread spread && visited.add(spread.getName())) {
                FragmentDefinition definition = execution.getFragmentsByName().get(spread.getName());
                if (definition != null) {
                    collect(definition.getSelectionSet(), execution, names, visited);
                }
            }
        }
    }

}
