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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanMethod;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Executable;
import io.micronaut.inject.ExecutableMethod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Alexey Zhokhov
 */
@Internal
final class MicronautUtils {

    static String getPropertyMethodName(BeanProperty<?, ?> beanProperty) {
        return "get" + beanProperty.getName().substring(0, 1).toUpperCase()
                + beanProperty.getName().substring(1) + "()";
    }

    static Argument<?> unwrapArgument(Argument<?> argument) {
        if (argument.isAsync()) {
            return argument.getFirstTypeVariable().get();
        }

        return argument;
    }

    static String getMethodName(Executable<?, ?> executable) {
        if (executable instanceof ExecutableMethod) {
            ExecutableMethod<?, ?> executableMethod = (ExecutableMethod<?, ?>) executable;
            return executableMethod.getName();
        } else if (executable instanceof BeanMethod) {
            BeanMethod<?, ?> beanMethod = (BeanMethod<?, ?>) executable;
            return beanMethod.getName();
        } else {
            throw new UnsupportedOperationException("Unsupported executable class: " + executable.getClass());
        }
    }

    static String getExecutableMethodFullName(Executable<?, ?> executable) {
        String args = Arrays.stream(executable.getArguments())
                .map(arg -> arg.getType().getName() + " " + arg.getName())
                .collect(Collectors.joining(", "));

        if (executable instanceof ExecutableMethod) {
            ExecutableMethod<?, ?> executableMethod = (ExecutableMethod<?, ?>) executable;
            return executableMethod.getName() + "(" + args + ")";
        } else if (executable instanceof BeanMethod) {
            BeanMethod<?, ?> beanMethod = (BeanMethod<?, ?>) executable;
            return beanMethod.getName() + "(" + args + ")";
        } else {
            throw new UnsupportedOperationException("Unsupported executable class: " + executable.getClass());
        }
    }

    static Map<Class<?>, List<String>> toMap(List<BeanDefinitionAndMethod> beanDefinitionAndMethods) {
        Map<Class<?>, List<String>> methods = new HashMap<>();

        for (BeanDefinitionAndMethod it : beanDefinitionAndMethods) {
            methods.putIfAbsent(it.getBeanDefinition().getBeanType(), new ArrayList<>());
            methods.get(it.getBeanDefinition().getBeanType())
                    .add(getExecutableMethodFullName(it.getExecutableMethod()));
        }

        return methods;
    }

    static Map<Class<?>, List<String>> toMap(List<BeanMethod<Object, ?>> beanMethods,
                                             @Nullable BeanProperty<Object, Object> beanProperty) {
        Map<Class<?>, List<String>> methods = new HashMap<>();

        if (beanProperty != null) {
            methods.putIfAbsent(beanProperty.getDeclaringBean().getBeanType(), new ArrayList<>());
            methods.get(beanProperty.getDeclaringBean().getBeanType()).add(getPropertyMethodName(beanProperty));
        }

        for (BeanMethod<Object, ?> it : beanMethods) {
            methods.putIfAbsent(it.getDeclaringBean().getBeanType(), new ArrayList<>());
            methods.get(it.getDeclaringBean().getBeanType()).add(getExecutableMethodFullName(it));
        }

        return methods;
    }

}
