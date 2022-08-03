/*
 * Copyright 2021-2022 original authors
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
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;

import java.util.HashSet;
import java.util.Set;

import static io.micronaut.core.util.ArgumentUtils.requireNonNull;

/**
 * @author Alexey Zhokhov
 */
@Internal
final class BeanDefinitionAndMethods {

    private final BeanDefinition<?> beanDefinition;
    private final Set<ExecutableMethod<Object, ?>> executableMethods = new HashSet<>();

    BeanDefinitionAndMethods(@NonNull BeanDefinition<?> beanDefinition) {
        requireNonNull("beanDefinition", beanDefinition);

        this.beanDefinition = beanDefinition;
    }

    void addExecutableMethod(@NonNull ExecutableMethod<Object, ?> executableMethod) {
        requireNonNull("executableMethod", executableMethod);

        executableMethods.add(executableMethod);
    }

    BeanDefinition<?> getBeanDefinition() {
        return beanDefinition;
    }

    Set<ExecutableMethod<Object, ?>> getExecutableMethods() {
        return executableMethods;
    }

}
