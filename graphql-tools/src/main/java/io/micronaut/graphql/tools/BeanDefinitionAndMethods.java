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
package io.micronaut.graphql.tools;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.graphql.tools.binding.internal.model.JMethod;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static io.micronaut.core.util.ArgumentUtils.requireNonNull;

@Internal
final class BeanDefinitionAndMethods {

    private final BeanDefinition<?> beanDefinition;
    // each method with the view that the rules read, built once at registration
    private final Map<ExecutableMethod<Object, ?>, JMethod> executableMethods = new HashMap<>();

    private final Function<ExecutableMethod<?, ?>, JMethod> views;

    BeanDefinitionAndMethods(@NonNull BeanDefinition<?> beanDefinition) {
        this(beanDefinition, new RuntimeTypeModel()::method);
    }

    BeanDefinitionAndMethods(@NonNull BeanDefinition<?> beanDefinition,
                             Function<ExecutableMethod<?, ?>, JMethod> views) {
        requireNonNull("beanDefinition", beanDefinition);

        this.beanDefinition = beanDefinition;
        this.views = views;
    }

    void addExecutableMethod(@NonNull ExecutableMethod<Object, ?> executableMethod) {
        requireNonNull("executableMethod", executableMethod);

        for (ExecutableMethod<Object, ?> existing : executableMethods.keySet()) {
            if (existing.getMethodName().equals(executableMethod.getMethodName())
                    && Arrays.equals(existing.getArgumentTypes(), executableMethod.getArgumentTypes())
                    && (!existing.isAbstract() || executableMethod.isAbstract())) {
                return;
            }
        }
        executableMethods.keySet().removeIf(it -> it.isAbstract()
                && it.getMethodName().equals(executableMethod.getMethodName())
                && Arrays.equals(it.getArgumentTypes(), executableMethod.getArgumentTypes()));
        executableMethods.put(executableMethod, views.apply(executableMethod));
    }

    BeanDefinition<?> getBeanDefinition() {
        return beanDefinition;
    }

    Set<ExecutableMethod<Object, ?>> getExecutableMethods() {
        return executableMethods.keySet();
    }

    Map<ExecutableMethod<Object, ?>, JMethod> getMethodViews() {
        return executableMethods;
    }

}
