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
package io.micronaut.graphql.tools.exceptions;

import io.micronaut.graphql.tools.MappingContext;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Alexey Zhokhov
 */
public final class MultipleMethodsFoundException extends AbstractMappingException {

    private final Map<Class<?>, List<String>> methods;

    public MultipleMethodsFoundException(MappingContext mappingContext, Map<Class<?>, List<String>> methods) {
        super("Found multiple methods for one GraphQL field.", mappingContext);

        this.methods = methods;
    }

    @Override
    public String getMessage() {
        StringBuilder result = new StringBuilder();

        int position = 0;
        for (Class<?> clazz : methods.keySet()) {
            for (String methodName : methods.get(clazz).stream().sorted().collect(Collectors.toList())) {
                result.append("\n  ").append(++position).append(") ").append(clazz.getName()).append(" ")
                        .append(methodName);
            }
        }

        return super.getMessage() + "\n  Methods: " + result;
    }

}
