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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.graphql.tools.execution.FieldDescriptor;
import io.micronaut.graphql.tools.execution.FieldFetcherDecorator;
import io.micronaut.graphql.tools.security.GraphQLSecurity;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
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
final class SecuredFieldDecorator implements FieldFetcherDecorator {

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
        String[] values = field.annotationMetadata().stringValues(Secured.class);
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
        return new SecuredDataFetcher(fetcher, field.coordinates().toString(), authenticated, roles);
    }

}
