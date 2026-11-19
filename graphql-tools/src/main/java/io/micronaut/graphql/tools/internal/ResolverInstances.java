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

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.internal.resolver.PerCallResolver;
import io.micronaut.inject.BeanDefinition;

/**
 * What a data fetcher invokes a resolver method on. A singleton, or a scoped proxy such as {@code @RequestScope} or
 * {@code @Refreshable}, is looked up once at binding and held directly. A bean of any other scope, such as
 * {@code @Prototype}, is looked up again for every call, so the scope decides the instance as it does for any other
 * injection point.
 *
 * @author Alexey Zhokhov
 */
@Internal
final class ResolverInstances {

    private ResolverInstances() {
    }

    static Object of(BeanContext beanContext, BeanDefinition<?> beanDefinition) {
        if (beanDefinition.isSingleton()) {
            return beanContext.getBean(beanDefinition);
        }
        return new PerCallResolver(() -> beanContext.getBean(beanDefinition));
    }

}
