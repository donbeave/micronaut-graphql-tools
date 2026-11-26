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
package io.micronaut.graphql.tools.mapping.model;

import io.micronaut.core.annotation.AccessorsStyle;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.graphql.tools.annotation.GraphQLInput;
import io.micronaut.graphql.tools.annotation.GraphQLType;

import java.util.List;

public final class JavaModelShapes {

    private JavaModelShapes() {
    }

    @Introspected
    public record Author(String name) {
    }

    @Introspected
    public record Book(String title, int pages, Author author, List<String> tags) {
    }

    @GraphQLInput
    public record BookFilter(String title, Integer minPages, List<String> tags) {
    }

    public sealed interface Payload permits Receipt, Failure {
    }

    @Introspected
    @GraphQLType
    public record Receipt(String code) implements Payload {
    }

    @Introspected
    @AccessorsStyle(readPrefixes = "")
    public sealed interface Failure extends Payload permits Timeout, Denied {
        String reason();
    }

    @Introspected
    public record Timeout(String reason, int seconds) implements Failure {
    }

    @Introspected
    public record Denied(String reason) implements Failure {
    }

    @Introspected
    @AccessorsStyle(readPrefixes = "")
    public sealed interface Shape permits Circle, Square {
        String name();
    }

    @Introspected
    public sealed interface Bare permits BareMember {
        String reason();
    }

    @Introspected
    public record BareMember(String reason) implements Bare {
    }

    @Introspected
    public record Circle(String name, double radius) implements Shape {
    }

    @Introspected
    public record Square(String name, double side) implements Shape {
    }

    @Introspected
    @AccessorsStyle(readPrefixes = "")
    public non-sealed interface Named extends Labelled {
        String name();
    }

    public sealed interface Labelled permits Named {
    }

    @Introspected
    public record RecordNamed(String name, int rank) implements Named {
    }

    @Introspected
    public static final class ClassNamed implements Named {
        private final String name;

        public ClassNamed(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        public String extra() {
            return "extra-" + name;
        }
    }

}
