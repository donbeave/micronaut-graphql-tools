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
package io.micronaut.graphql.tools.internal.error;

import graphql.GraphQLError;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.Ordered;
import io.micronaut.graphql.tools.error.GraphQLExceptionContext;
import io.micronaut.graphql.tools.error.GraphQLExceptionMapper;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps a {@code ConstraintViolationException} (Micronaut Validation on a resolver method or an input class) to one error
 * for each violation, with the argument that failed and the path inside it. The message of a violation is the message of the
 * constraint, written to be shown to a user. Present only when Jakarta Validation is on the classpath, and last in the order, so that an
 * application's own mapper for the exception wins.
 */
@Internal
@Singleton
@Requires(classes = ConstraintViolationException.class)
final class ConstraintViolationExceptionMapper implements GraphQLExceptionMapper<ConstraintViolationException>, Ordered {

    static final String CODE = "CONSTRAINT_VIOLATION";

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public GraphQLError map(ConstraintViolationException exception, GraphQLExceptionContext context) {
        List<GraphQLError> errors = mapAll(exception, context);
        return errors == null ? null : errors.get(0);
    }

    @Override
    public List<GraphQLError> mapAll(ConstraintViolationException exception, GraphQLExceptionContext context) {
        List<GraphQLError> errors = new ArrayList<>();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            List<String> names = new ArrayList<>();
            for (Path.Node node : violation.getPropertyPath()) {
                if (node.getKind() != ElementKind.METHOD && node.getKind() != ElementKind.CONSTRUCTOR && node.getName() != null) {
                    names.add(node.getName());
                }
            }
            Map<String, Object> extensions = new LinkedHashMap<>();
            extensions.put("code", CODE);
            if (!names.isEmpty()) {
                extensions.put("argument", names.get(0));
                extensions.put("inputPath", List.copyOf(names.subList(1, names.size())));
            }
            errors.add(context.newError().message(violation.getMessage()).extensions(extensions).build());
        }
        return errors.isEmpty() ? null : errors;
    }

}
