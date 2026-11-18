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
package io.micronaut.graphql.tools.binding.internal.plan;

import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.binding.internal.model.JClass;

import java.util.List;

/**
 * An enabled resolver bean.
 *
 * @param type            the class of the bean
 * @param beanDescription the bean as messages print it
 * @param contractTypes   the binary names of the model classes that the bean resolves, for a type resolver
 */
@Internal
public record ResolverBean(JClass type, String beanDescription, List<String> contractTypes) {

    public ResolverBean {
        contractTypes = List.copyOf(contractTypes);
    }
}
