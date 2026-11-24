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
package io.micronaut.graphql.tools.security.internal;

import graphql.schema.DataFetcher;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.execution.FieldDescriptor;
import io.micronaut.graphql.tools.execution.GraphQLFieldFetcherDecorator;
import io.micronaut.graphql.tools.security.GraphQLSecurity;
import io.micronaut.inject.annotation.EvaluatedAnnotationValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.security.token.DefaultRolesFinder;
import io.micronaut.security.token.RolesFinder;
import jakarta.inject.Singleton;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides at startup, for each field, whether it is secured. A field whose resolver method (or class) carries no
 * {@code @Secured}, or one that allows anonymous callers, keeps the fetcher it was offered: the same instance, with nothing
 * added to the call. A field with the annotation gets a {@link SecuredDataFetcher} that holds the decision, computed here.
 */
@Internal
@Singleton
@Requires(classes = {Secured.class, Authentication.class})
@Requires(property = GraphQLSecurity.PREFIX + ".enabled", value = "true", defaultValue = "true")
final class SecuredFieldDecorator implements GraphQLFieldFetcherDecorator {

    private static final String EXPRESSION_PREFIX = "#{";

    private final RolesFinder rolesFinder;

    /**
     * @param rolesFinder the {@code RolesFinder} bean of the context, so that an application that replaces it decides on the
     *                    roles of secured fields as it does on those of routes. The default bean keeps the plain lookup, and so
     *                    does a context without the bean (Micronaut Security switched off)
     */
    SecuredFieldDecorator(@Nullable RolesFinder rolesFinder) {
        this.rolesFinder = rolesFinder == null || rolesFinder.getClass() == DefaultRolesFinder.class ? null : rolesFinder;
    }

    @Override
    public boolean decoratesFieldsWithoutArguments() {
        return true;
    }

    @Override
    public boolean decoratesTrivialFields() {
        return true;
    }

    @Override
    @NonNull
    public DataFetcher<Object> decorate(@NonNull FieldDescriptor field, @NonNull DataFetcher<Object> fetcher) {
        if (field.annotationMetadata().getAnnotation(Secured.class) instanceof EvaluatedAnnotationValue<?>) {
            rejectExpression(field);
        }
        String[] values = field.annotationMetadata().stringValues(Secured.class);
        for (String value : values) {
            if (value.startsWith(EXPRESSION_PREFIX)) {
                rejectExpression(field);
            }
        }
        if (values.length == 0 || Arrays.asList(values).contains(SecurityRule.IS_ANONYMOUS)) {
            return fetcher;
        }
        boolean authenticated = false;
        Set<String> roles = Set.of();
        if (!Arrays.asList(values).contains(SecurityRule.DENY_ALL)) {
            authenticated = Arrays.asList(values).contains(SecurityRule.IS_AUTHENTICATED);
            roles = Arrays.stream(values)
                    .filter(value -> !SecurityRule.IS_AUTHENTICATED.equals(value))
                    .collect(Collectors.toUnmodifiableSet());
        }
        return new SecuredDataFetcher(fetcher, field.coordinates().toString(), authenticated, roles, rolesFinder);
    }

    private static void rejectExpression(FieldDescriptor field) {
        throw new ConfigurationException("@Secured on the resolver method " + field.resolver() + " (field " + field.coordinates() + ") has an expression value "
                + "(one that starts with #{). Micronaut Security evaluates an expression against the HTTP route, and a GraphQL "
                + "field has none, so the module does not evaluate expressions. Use SecurityRule.IS_AUTHENTICATED, "
                + "SecurityRule.IS_ANONYMOUS, SecurityRule.DENY_ALL or role names");
    }

}
