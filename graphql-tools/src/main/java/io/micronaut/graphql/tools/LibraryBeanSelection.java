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

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides when the library's own {@code GraphQLSchema} and {@code GraphQL} beans step aside. They step aside only for
 * an application bean of that type that competes for an unqualified injection point: a bean without a qualifier, or a
 * {@code @Primary} one. A bean with a qualifier (for example {@code @Named("admin")}) is a secondary bean that is
 * injected by its qualifier, so it does not replace the library's bean.
 */
@Internal
public final class LibraryBeanSelection {

    private LibraryBeanSelection() {
    }

    /**
     * @param context the bean context to search
     * @param type    the bean type
     * @return the application bean definition that makes the library's bean of this type step aside, or null
     */
    @Nullable
    static BeanDefinition<?> competitor(BeanContext context, Class<?> type) {
        BeanDefinition<?> competitor = null;
        for (BeanDefinition<?> definition : applicationBeans(context, type)) {
            if (definition.hasStereotype(Primary.class)) {
                return definition;
            }
            if (competitor == null && definition.getDeclaredQualifier() == null) {
                competitor = definition;
            }
        }
        return competitor;
    }

    /**
     * The enabled beans of the type that the library does not create. It reads the bean definition references, not
     * {@code getBeanDefinitions}, because the latter evaluates the library's own conditions, which call this method.
     *
     * @param context the bean context to search
     * @param type    the bean type
     * @return the application's bean definitions of this type
     */
    static List<BeanDefinition<?>> applicationBeans(BeanContext context, Class<?> type) {
        List<BeanDefinition<?>> beans = new ArrayList<>();
        for (BeanDefinitionReference<?> reference : context.getBeanDefinitionReferences()) {
            if (type.isAssignableFrom(reference.getBeanType()) && reference.isPresent()) {
                BeanDefinition<?> definition = reference.load();
                // the library's own references are skipped before isEnabled, which would evaluate this condition again
                if (definition != null && !isLibrary(definition) && reference.isEnabled(context)) {
                    beans.add(definition);
                }
            }
        }
        return beans;
    }

    static boolean isLibrary(BeanDefinition<?> definition) {
        return definition.getDeclaringType().filter(GraphQLFactory.class::equals).isPresent();
    }

    static String describe(BeanDefinition<?> definition) {
        String origin = definition.getDeclaringType()
                .map(declaring -> declaring.getName() + "#" + definition.getBeanType().getSimpleName())
                .orElseGet(() -> definition.getBeanType().getName());
        return origin + qualifierSuffix(definition);
    }

    private static String qualifierSuffix(BeanDefinition<?> definition) {
        String suffix = definition.getDeclaredQualifier() == null ? "" : " with qualifier " + definition.getDeclaredQualifier();
        return definition.hasStereotype(Primary.class) ? suffix + " (@Primary)" : suffix;
    }

    /** Matches when no application bean of {@code GraphQLSchema} competes with the library's schema. */
    public static final class NoCompetingSchema implements Condition {
        @Override
        public boolean matches(ConditionContext context) {
            return noCompetitor(context, graphql.schema.GraphQLSchema.class);
        }
    }

    /** Matches when no application bean of {@code GraphQL} competes with the library's engine. */
    public static final class NoCompetingGraphQL implements Condition {
        @Override
        public boolean matches(ConditionContext context) {
            return noCompetitor(context, graphql.GraphQL.class);
        }
    }

    private static boolean noCompetitor(ConditionContext<?> context, Class<?> type) {
        BeanDefinition<?> competitor = competitor(context.getBeanContext(), type);
        if (competitor == null) {
            return true;
        }
        context.fail("the application provides " + describe(competitor));
        return false;
    }

}
