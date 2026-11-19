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
package io.micronaut.graphql.tools.internal;

import graphql.language.InterfaceTypeDefinition;
import io.micronaut.core.annotation.Internal;

/**
 * A type resolver method of an interface that can bind a field of an object type that implements it.
 *
 * @param interfaceTypeDefinition the interface
 * @param interfaceClass          the Java class that the interface is bound to
 * @param method                  the resolver method
 */
@Internal
record InterfaceResolverMethod(InterfaceTypeDefinition interfaceTypeDefinition, Class<?> interfaceClass,
                                       BeanDefinitionAndMethod method) {
}
