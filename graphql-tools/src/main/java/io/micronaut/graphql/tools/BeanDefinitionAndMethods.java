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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static io.micronaut.core.util.ArgumentUtils.requireNonNull;

@Internal
final class BeanDefinitionAndMethods {

    private final BeanDefinition<?> beanDefinition;
    // each method with the view that the rules read, built once at registration
    private final Map<ExecutableMethod<Object, ?>, JMethod> executableMethods = new HashMap<>();
    // the registered method of each name and parameter types, and the methods by name in the order of the map above
    private final Map<List<Object>, ExecutableMethod<Object, ?>> bySignature = new HashMap<>();
    private Map<String, List<Map.Entry<ExecutableMethod<Object, ?>, JMethod>>> byName;

    private final Function<ExecutableMethod<?, ?>, JMethod> views;
    private Function<JMethod, List<String>> bindingNames = it -> List.of(it.name());

    BeanDefinitionAndMethods(@NonNull BeanDefinition<?> beanDefinition) {
        this(beanDefinition, new RuntimeTypeModel()::method);
    }

    BeanDefinitionAndMethods(@NonNull BeanDefinition<?> beanDefinition,
                             Function<ExecutableMethod<?, ?>, JMethod> views) {
        requireNonNull("beanDefinition", beanDefinition);

        this.beanDefinition = beanDefinition;
        this.views = views;
    }

    /**
     * @param bindingNames the names of the fields a method can bind; the index by name is rebuilt on the next lookup
     */
    void bindingNames(Function<JMethod, List<String>> bindingNames) {
        this.bindingNames = bindingNames;
        byName = null;
    }

    void resetIndex() {
        byName = null;
    }

    void addExecutableMethod(@NonNull ExecutableMethod<Object, ?> executableMethod) {
        requireNonNull("executableMethod", executableMethod);

        List<Object> signature = signature(executableMethod);
        ExecutableMethod<Object, ?> existing = bySignature.get(signature);
        if (existing != null) {
            if (!existing.isAbstract() || executableMethod.isAbstract()) {
                return;
            }
            executableMethods.remove(existing);
        }
        bySignature.put(signature, executableMethod);
        byName = null;
        executableMethods.put(executableMethod, views.apply(executableMethod));
    }

    private static List<Object> signature(ExecutableMethod<Object, ?> method) {
        return List.of(method.getMethodName(), Arrays.asList(method.getArgumentTypes()));
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

    /**
     * @param name the name of a field
     * @return the methods that can bind a field of the name, with their views, in the iteration order of
     * {@link #getMethodViews()}
     */
    List<Map.Entry<ExecutableMethod<Object, ?>, JMethod>> methodsNamed(String name) {
        if (byName == null) {
            Map<String, List<Map.Entry<ExecutableMethod<Object, ?>, JMethod>>> index = new HashMap<>();
            for (Map.Entry<ExecutableMethod<Object, ?>, JMethod> entry : executableMethods.entrySet()) {
                for (String bound : bindingNames.apply(entry.getValue())) {
                    index.computeIfAbsent(bound, it -> new ArrayList<>()).add(entry);
                }
            }
            byName = index;
        }
        return byName.getOrDefault(name, List.of());
    }

}
