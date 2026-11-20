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
package io.micronaut.graphql.tools.annotation;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.inject.BeanDefinition;

/**
 * The condition behind {@link GraphQLRootResolver}, {@link GraphQLTypeResolver} and the marker interfaces of
 * {@code io.micronaut.graphql.tools.resolver}: a class that carries one, or inherits one, is a bean only when it has a scope
 * of its own, for example {@code @Singleton}, or is declared a bean, as a factory method is. The annotations are executable,
 * which alone makes Micronaut create a bean definition for any class that carries them, so this condition keeps a fake, a
 * decorator or a second implementation of a contract from becoming a second resolver. An application does not refer to it.
 *
 * <p>The class is introspected, so that Micronaut creates it without reflection, also in a native image.</p>
 */
@Introspected
public final class ResolverBeanCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context) {
        AnnotationMetadataProvider component = context.getComponent();
        if (component instanceof BeanDefinition<?> definition) {
            return definition.getScope().isPresent() || definition.hasStereotype(Bean.class);
        }
        return true;
    }
}
