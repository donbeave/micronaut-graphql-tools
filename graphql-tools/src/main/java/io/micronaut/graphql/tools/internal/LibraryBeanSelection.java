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

import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import io.micronaut.configuration.graphql.DefaultGraphQLInvocation;
import io.micronaut.configuration.graphql.GraphQLInvocation;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.value.PropertyCatalog;
import io.micronaut.graphql.tools.internal.execution.LibraryGraphQLInvocation;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.qualifiers.Qualifiers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Decides when the library's own {@code GraphQLSchema} and {@code GraphQL} beans step aside. They step aside only for
 * an application bean of that type that competes for an unqualified injection point: a bean without a qualifier, or a
 * {@code @Primary} one. A bean with a qualifier (for example {@code @Named("admin")}) is a secondary bean that is
 * injected by its qualifier, so it does not replace the library's bean.
 *
 * <p>The beans come from the bean definition references, which include the beans registered at runtime. A bean that an
 * enabled {@code @Replaces} bean replaces is not counted, and an {@code @EachProperty} or {@code @EachBean} template is
 * judged by the beans it expands to.
 */
@Internal
public final class LibraryBeanSelection {

    private LibraryBeanSelection() {
    }

    /**
     * @param context the bean context to search
     * @param type    the bean type
     * @return the application bean that makes the library's bean of this type step aside, or null
     */
    @Nullable
    public static Provided competitor(BeanContext context, Class<?> type) {
        return competitor(context, type, new Visits());
    }

    @Nullable
    private static Provided competitor(BeanContext context, Class<?> type, Visits visits) {
        if (!visits.judging().add(type)) {
            return null;
        }
        try {
            Provided competitor = null;
            for (Provided bean : provided(context, type, false, visits)) {
                if (bean.primary()) {
                    return bean;
                }
                if (competitor == null && bean.qualifier() == null) {
                    competitor = bean;
                }
            }
            return competitor;
        } finally {
            visits.judging().remove(type);
        }
    }

    /**
     * The enabled beans of the type that the library does not create. It reads the bean definition references, not
     * {@code getBeanDefinitions}, because the latter evaluates the library's own conditions, which call this method.
     *
     * @param context the bean context to search
     * @param type    the bean type
     * @return the application's beans of this type
     */
    static List<Provided> applicationBeans(BeanContext context, Class<?> type) {
        return provided(context, type, false, new Visits());
    }

    /**
     * The invocations that are not the application's: the library's own, and the transport's default, which the
     * library's invocation replaces. An {@code @EachBean(GraphQLInvocation.class)} template is not judged by them.
     */
    private static boolean isInvocationOfLibraryOrTransport(BeanDefinition<?> definition) {
        Class<?> beanType = definition.getBeanType();
        return beanType == LibraryGraphQLInvocation.class
                || beanType == DefaultGraphQLInvocation.class && definition.getDeclaringType().isEmpty();
    }

    private static List<Provided> provided(BeanContext context, Class<?> type, boolean withLibrary, Visits visits) {
        List<BeanDefinition<?>> enabled = new ArrayList<>();
        for (BeanDefinitionReference<?> reference : context.getBeanDefinitionReferences()) {
            if (type.isAssignableFrom(reference.getBeanType()) && reference.isPresent()) {
                BeanDefinition<?> definition = reference.load();
                // the library's own references are never asked isEnabled, which would evaluate its condition again
                if (definition != null && !isInvocationOfLibraryOrTransport(definition) && (isLibrary(definition)
                        ? withLibrary && libraryEnabled(context, reference, definition, visits)
                        : reference.isEnabled(context))) {
                    enabled.add(definition);
                }
            }
        }
        List<Provided> beans = new ArrayList<>();
        for (BeanDefinition<?> definition : enabled) {
            expand(context, definition, visits, beans);
        }
        return withoutReplaced(beans);
    }

    private static boolean libraryEnabled(BeanContext context, BeanDefinitionReference<?> reference,
                                          BeanDefinition<?> definition, Visits visits) {
        Class<?> type = definition.getBeanType();
        if (type == GraphQLSchema.class || type == GraphQL.class) {
            return competitor(context, type, visits) == null;
        }
        return reference.isEnabled(context);
    }

    private static List<Provided> withoutReplaced(List<Provided> beans) {
        List<Provided> kept = new ArrayList<>(beans);
        for (Provided replacing : beans) {
            AnnotationValue<Replaces> replaces = replacing.definition().getAnnotation(Replaces.class);
            if (replaces != null) {
                kept.removeIf(candidate -> candidate.definition() != replacing.definition() && replaces(replaces, replacing.definition(), candidate));
            }
        }
        return kept;
    }

    private static boolean replaces(AnnotationValue<Replaces> replaces, BeanDefinition<?> replacing, Provided candidate) {
        Class<?> replaced = replaces.classValue("bean").orElse(replacing.getBeanType());
        if (replaces.annotationClassValue("qualifier").isPresent()) {
            return false;
        }
        Optional<Class<?>> factory = replaces.classValue("factory").map(type -> (Class<?>) type);
        if (factory.isPresent() && candidate.definition().getDeclaringType().filter(factory.get()::equals).isEmpty()) {
            return false;
        }
        Optional<String> named = replaces.stringValue("named");
        if (named.isPresent()) {
            return qualifiedByName(named.get(), replaced, candidate);
        }
        return candidate.definition().getBeanType() == replaced;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean qualifiedByName(String name, Class<?> replaced, Provided candidate) {
        Qualifier byName = Qualifiers.byName(name);
        return replaced.isAssignableFrom(candidate.definition().getBeanType())
                && (byName.equals(candidate.qualifier()) || byName.doesQualify(replaced, candidate.definition()));
    }

    private static void expand(BeanContext context, BeanDefinition<?> definition, Visits visits, List<Provided> beans) {
        if (definition.hasStereotype(EachProperty.class)) {
            expandProperties(context, definition, beans);
        } else if (definition.hasStereotype(EachBean.class)) {
            expandTargets(context, definition, visits, beans);
        } else {
            beans.add(new Provided(definition));
        }
    }

    private static void expandProperties(BeanContext context, BeanDefinition<?> template, List<Provided> beans) {
        String prefix = template.stringValue(ConfigurationReader.class, ConfigurationReader.PREFIX)
                .filter(value -> value.endsWith(".*") || value.endsWith("[*]"))
                .map(value -> value.substring(0, value.length() - (value.endsWith("[*]") ? 3 : 2))).orElse(null);
        // a nested template (a prefix with another star) is not expanded here and stays unqualified
        if (prefix == null || prefix.contains("*") || !(context instanceof ApplicationContext applicationContext)) {
            beans.add(new Provided(template));
            return;
        }
        String primary = template.stringValue(EachProperty.class, "primary").orElse(null);
        PropertyCatalog catalog = template.enumValue(EachProperty.class, "catalog", PropertyCatalog.class).orElse(PropertyCatalog.NORMALIZED);
        boolean templatePrimary = template.hasStereotype(Primary.class);
        for (String key : applicationContext.getEnvironment().getPropertyEntries(prefix, catalog)) {
            beans.add(new Provided(template, Qualifiers.byName(key), templatePrimary || key.equals(primary)));
        }
    }

    private static void expandTargets(BeanContext context, BeanDefinition<?> template, Visits visits, List<Provided> beans) {
        Optional<Class<?>> target = template.classValue(EachBean.class).map(type -> (Class<?>) type);
        if (target.isEmpty()) {
            beans.add(new Provided(template));
        } else if (visits.expanding().add(template)) {
            try {
                for (Provided targetBean : provided(context, target.get(), true, visits)) {
                    beans.add(new Provided(template, targetBean.qualifier(),
                            template.hasStereotype(Primary.class) || targetBean.primary()));
                }
            } finally {
                visits.expanding().remove(template);
            }
        }
    }

    private static boolean replacesDefault(BeanDefinition<?> definition) {
        AnnotationValue<Replaces> replaces = definition.getAnnotation(Replaces.class);
        return replaces != null && replaces.classValue("bean").filter(DefaultGraphQLInvocation.class::equals).isPresent();
    }

    static boolean isLibrary(BeanDefinition<?> definition) {
        return definition.getDeclaringType().filter(GraphQLFactory.class::equals).isPresent();
    }

    /**
     * @param definition the bean definition
     * @return the description of an unqualified bean of this definition
     */
    public static String describe(BeanDefinition<?> definition) {
        return describe(new Provided(definition));
    }

    public static String describe(Provided bean) {
        BeanDefinition<?> definition = bean.definition();
        String origin = definition.getDeclaringType()
                .map(declaring -> declaring.getName() + "#" + definition.getBeanType().getSimpleName())
                .orElseGet(() -> definition.getBeanType().getName());
        String suffix = bean.qualifier() == null ? "" : " with qualifier " + bean.qualifier();
        return bean.primary() ? origin + suffix + " (@Primary)" : origin + suffix;
    }

    private static boolean noCompetitor(ConditionContext<?> context, Class<?> type) {
        Provided competitor = competitor(context.getBeanContext(), type);
        if (competitor == null) {
            return true;
        }
        context.fail("the application provides " + describe(competitor));
        return false;
    }

    /**
     * An application bean that the context can resolve: a bean definition, or one expansion of an {@code @EachProperty}
     * or {@code @EachBean} template.
     *
     * @param definition the bean definition, or the template
     * @param qualifier  the qualifier of the bean, or null
     * @param primary    whether the bean is primary
     */
    public record Provided(BeanDefinition<?> definition, @Nullable Qualifier<?> qualifier, boolean primary) {
        Provided(BeanDefinition<?> definition) {
            this(definition, definition.getDeclaredQualifier(), definition.hasStereotype(Primary.class));
        }
    }

    /**
     * What is being resolved, so that templates and library beans that depend on each other end the recursion.
     *
     * @param judging   the bean types whose competitor is being looked for
     * @param expanding the templates being expanded
     */
    private record Visits(Set<Class<?>> judging, Set<BeanDefinition<?>> expanding) {
        Visits() {
            this(new HashSet<>(), new HashSet<>());
        }
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

    /**
     * Matches unless the application declares its own {@code DefaultGraphQLInvocation} bean through a factory, for
     * example a {@code @Primary} factory method. The library's invocation replaces the transport's
     * {@code DefaultGraphQLInvocation} by type, which would also replace that bean, so it steps aside. The transport's
     * own bean is a class bean, declared by itself.
     */
    public static final class NoApplicationDefaultInvocation implements Condition {
        @Override
        public boolean matches(ConditionContext context) {
            // every invocation is read, so that a bean that replaces the factory's default is seen; a template
            // (@EachProperty, @EachBean) counts by the beans it expands to
            for (Provided bean : provided(context.getBeanContext(), GraphQLInvocation.class, false, new Visits())) {
                BeanDefinition<?> definition = bean.definition();
                Optional<Class<?>> declaring = definition.getDeclaringType();
                // only a bean that outranks the transport's class bean (@Primary) or replaces it makes the library's
                // invocation step aside; an unqualified one does not, and the library's invocation replaces both
                if (definition.getBeanType() == DefaultGraphQLInvocation.class
                        && declaring.isPresent() && declaring.get() != DefaultGraphQLInvocation.class
                        && (bean.primary() || replacesDefault(definition))) {
                    context.fail("the application declares " + declaring.get().getName()
                            + "#DefaultGraphQLInvocation, which the library's invocation would replace");
                    return false;
                }
            }
            return true;
        }
    }

}
