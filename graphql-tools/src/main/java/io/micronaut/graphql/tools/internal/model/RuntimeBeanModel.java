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
package io.micronaut.graphql.tools.internal.model;

import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.binding.internal.plan.BeanModel;
import io.micronaut.graphql.tools.binding.internal.plan.ResolverBean;
import io.micronaut.inject.BeanDefinition;

import java.util.List;

/**
 * The runtime implementation of {@link BeanModel}: the resolver beans that the registry holds are the enabled ones,
 * because the processors register only beans that survived their conditions and replacements.
 */
@Internal
public final class RuntimeBeanModel implements BeanModel {

    private final GraphQLResolversRegistry registry;
    private final RuntimeTypeModel typeModel;

    RuntimeBeanModel(GraphQLResolversRegistry registry, RuntimeTypeModel typeModel) {
        this.registry = registry;
        this.typeModel = typeModel;
    }

    @Override
    public List<ResolverBean> rootResolvers() {
        return registry.rootResolverBeans().stream().map(this::bean).toList();
    }

    @Override
    public List<ResolverBean> typeResolvers() {
        return registry.typeResolverBeans().stream().map(this::bean).toList();
    }

    private ResolverBean bean(BeanDefinition<?> beanDefinition) {
        return new ResolverBean(typeModel.of(beanDefinition),
                GraphQLResolversRegistry.getUserBeanType(beanDefinition).getName(),
                registry.contractTypesOf(beanDefinition));
    }
}
