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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps a {@code ConstraintViolationException} (Micronaut Validation on a resolver method or an input class) to one error
 * for each violation, with the argument that failed and the path inside it (property names, list indexes and map keys). The errors are in the order of the arguments, then of the path, because the validator returns the violations in no fixed order. The message of a violation is the message of the
 * constraint, written to be shown to a user. Present only when Jakarta Validation is on the classpath, and last in the order, so that an
 * application's own mapper for the exception wins.
 */
@Internal
@Singleton
@Requires(classes = ConstraintViolationException.class)
final class ConstraintViolationExceptionMapper implements GraphQLExceptionMapper<ConstraintViolationException>, Ordered {

    static final String CODE = "CONSTRAINT_VIOLATION";

    private static final Comparator<ConstraintViolation<?>> ORDER = Comparator
            .<ConstraintViolation<?>>comparingInt(ConstraintViolationExceptionMapper::parameterIndex)
            .thenComparing(violation -> violation.getPropertyPath().toString())
            .thenComparing(ConstraintViolation::getMessage);

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
        List<ConstraintViolation<?>> violations = new ArrayList<>(exception.getConstraintViolations());
        violations.sort(ORDER);
        List<GraphQLError> errors = new ArrayList<>(violations.size());
        for (ConstraintViolation<?> violation : violations) {
            String argument = null;
            List<Object> inputPath = new ArrayList<>();
            for (Path.Node node : violation.getPropertyPath()) {
                if (node.getKind() == ElementKind.METHOD || node.getKind() == ElementKind.CONSTRUCTOR || node.getName() == null) {
                    continue;
                }
                if (argument == null) {
                    argument = node.getName();
                    continue;
                }
                if (node.isInIterable()) {
                    if (node.getIndex() != null) {
                        inputPath.add(node.getIndex());
                    } else if (node.getKey() != null) {
                        inputPath.add(node.getKey());
                    }
                }
                if (node.getKind() != ElementKind.CONTAINER_ELEMENT) {
                    inputPath.add(node.getName());
                }
            }
            Map<String, Object> extensions = new LinkedHashMap<>();
            extensions.put("code", CODE);
            if (argument != null) {
                extensions.put("argument", argument);
                extensions.put("inputPath", List.copyOf(inputPath));
            }
            errors.add(context.newError().message(violation.getMessage()).extensions(extensions).build());
        }
        return errors.isEmpty() ? null : errors;
    }

    private static int parameterIndex(ConstraintViolation<?> violation) {
        for (Path.Node node : violation.getPropertyPath()) {
            if (node instanceof Path.ParameterNode parameter) {
                return parameter.getParameterIndex();
            }
        }
        return -1;
    }

}
