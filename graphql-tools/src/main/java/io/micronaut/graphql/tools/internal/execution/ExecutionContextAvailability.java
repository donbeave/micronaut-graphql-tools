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
package io.micronaut.graphql.tools.internal.execution;

import io.micronaut.configuration.graphql.GraphQLInvocation;
import io.micronaut.context.BeanContext;
import io.micronaut.context.exceptions.NonUniqueBeanException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.internal.LibraryBeanSelection;
import io.micronaut.graphql.tools.internal.exceptions.configuration.ExecutionSetupException;

/**
 * Whether the library's invocation runs the operations, so that the {@link ExecutionState} exists. A resolver that
 * needs the execution context (a DataLoader, the request, the response or the principal) asks at startup and fails
 * with {@code execution-context-unavailable} when the invocation that serves the transport is not the library's.
 */
@Internal
public final class ExecutionContextAvailability {

    private ExecutionContextAvailability() {
    }

    /**
     * @param context the bean context
     * @return true when the invocation that serves the transport is the library's
     */
    public static boolean isAvailable(BeanContext context) {
        return selected(context) == null;
    }

    /**
     * Fails the startup when the execution context is not available.
     *
     * @param context  the bean context
     * @param location where the need is, for example {@code Query.book (parameter 'request' of Books#book)}
     * @param need     what is needed, for example {@code a DataLoader} or {@code the HTTP request}
     * @throws ExecutionSetupException with code {@code execution-context-unavailable}
     */
    public static void require(BeanContext context, String location, String need) {
        BeanDefinition<?> selected = selected(context);
        if (selected != null) {
            throw new ExecutionSetupException(BindingCode.EXECUTION_CONTEXT_UNAVAILABLE, location + " needs " + need
                    + ", which the library's GraphQLInvocation provides. The application provides "
                    + LibraryBeanSelection.describe(selected) + " instead, so the library's invocation is not used. "
                    + "Fix: remove that need from the resolver, or remove the application's GraphQLInvocation "
                    + "(@Replaces(DefaultGraphQLInvocation.class) or @Primary).");
        }
    }

    /**
     * The definition of the invocation that an unqualified injection point gets, read without creating the bean, which
     * needs the schema that is being bound. An ambiguous choice is left to the injection point, which fails there.
     */
    @Nullable
    private static BeanDefinition<?> selected(BeanContext context) {
        try {
            BeanDefinition<?> definition = context.findBeanDefinition(GraphQLInvocation.class).orElse(null);
            return definition == null || definition.getBeanType() == LibraryGraphQLInvocation.class ? null : definition;
        } catch (NonUniqueBeanException e) {
            return null;
        }
    }

}
