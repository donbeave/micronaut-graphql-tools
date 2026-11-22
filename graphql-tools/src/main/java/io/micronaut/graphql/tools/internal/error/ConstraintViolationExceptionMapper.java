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
import java.util.Set;

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

    /**
     * The names that Jakarta Validation and Micronaut Validation give the elements of their own containers. A container
     * element with another name, even one in angle brackets, comes from a custom {@code ValueExtractor}, and the label
     * is part of the path.
     */
    private static final Set<String> STANDARD_ELEMENT_NAMES = Set.of("<list element>", "<iterable element>", "<map key>",
            "<map value>", "<array element>", "<completion stage element>", "<publisher element>");

    private static final Comparator<Located> ORDER = Comparator
            .<Located>comparingInt(located -> located.parameterIndex)
            .thenComparing(located -> located.inputPath, ConstraintViolationExceptionMapper::comparePaths)
            .thenComparing(located -> located.violation.getMessage());

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
        List<Located> violations = new ArrayList<>(exception.getConstraintViolations().size());
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            violations.add(locate(violation));
        }
        violations.sort(ORDER);
        List<GraphQLError> errors = new ArrayList<>(violations.size());
        for (Located located : violations) {
            Map<String, Object> extensions = new LinkedHashMap<>();
            extensions.put("code", CODE);
            if (located.argument != null) {
                extensions.put("argument", located.argument);
                extensions.put("inputPath", located.inputPath);
            }
            errors.add(context.newError().message(located.violation.getMessage()).extensions(extensions).build());
        }
        return errors.isEmpty() ? null : errors;
    }

    private static Located locate(ConstraintViolation<?> violation) {
        String argument = null;
        int parameterIndex = -1;
        List<Object> inputPath = new ArrayList<>();
        for (Path.Node node : violation.getPropertyPath()) {
            if (node instanceof Path.ParameterNode parameter) {
                parameterIndex = parameter.getParameterIndex();
            }
            String name = node.getName();
            if (node.getKind() == ElementKind.METHOD || node.getKind() == ElementKind.CONSTRUCTOR) {
                continue;
            }
            if (argument == null) {
                argument = name;
                continue;
            }
            boolean element = node.getKind() == ElementKind.CONTAINER_ELEMENT;
            boolean named = name != null && (!element || !STANDARD_ELEMENT_NAMES.contains(name));
            if (element && named) {
                inputPath.add(unbracketed(name));
            }
            if (node.isInIterable()) {
                if (node.getIndex() != null) {
                    inputPath.add(node.getIndex());
                } else if (node.getKey() != null) {
                    inputPath.add(String.valueOf(node.getKey()));
                }
            }
            if (named && !element) {
                inputPath.add(name);
            }
        }
        return new Located(violation, argument, parameterIndex, List.copyOf(inputPath));
    }

    /**
     * The label of a container element that a custom {@code ValueExtractor} gave it, without the angle brackets that
     * some extractors put around the name, so that no extractor name appears in the path with them.
     */
    private static String unbracketed(String name) {
        if (name.length() > 2 && name.startsWith("<") && name.endsWith(">")) {
            return name.substring(1, name.length() - 1);
        }
        return name;
    }

    private static int comparePaths(List<Object> left, List<Object> right) {
        int shared = Math.min(left.size(), right.size());
        for (int i = 0; i < shared; i++) {
            int order = compareElements(left.get(i), right.get(i));
            if (order != 0) {
                return order;
            }
        }
        return Integer.compare(left.size(), right.size());
    }

    private static int compareElements(Object left, Object right) {
        if (left instanceof Integer a && right instanceof Integer b) {
            return a.compareTo(b);
        }
        if (left instanceof Integer) {
            return -1;
        }
        if (right instanceof Integer) {
            return 1;
        }
        return String.valueOf(left).compareTo(String.valueOf(right));
    }

    private record Located(ConstraintViolation<?> violation, String argument, int parameterIndex, List<Object> inputPath) {
    }

}
