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
package io.micronaut.graphql.tools.codegen.ir;

import java.util.Objects;

/**
 * The return shape of a contract method: the type itself by default, a {@code suspend} function when the
 * {@code kotlinSuspend} option asks for it, and the type argument of a class for a subscription.
 *
 * @param shape     the shape
 * @param className for {@link Shape#GENERIC} the qualified name of the class that wraps the return type; {@code null} otherwise
 */
public record Wrapper(Shape shape, String className) {

    /** The method returns the type itself. */
    public static final Wrapper PLAIN = new Wrapper(Shape.PLAIN, null);

    /** The method is a Kotlin {@code suspend} function. */
    public static final Wrapper SUSPEND = new Wrapper(Shape.SUSPEND, null);

    /**
     * Creates a wrapper.
     */
    public Wrapper {
        Objects.requireNonNull(shape, "shape");
        if ((shape == Shape.GENERIC) != (className != null)) {
            throw new IllegalArgumentException("className is given exactly for the shape GENERIC");
        }
    }

    /**
     * A return type that is a generic class with the type of the field as its argument.
     *
     * @param className the qualified name of the class
     * @return the wrapper
     */
    public static Wrapper generic(String className) {
        return new Wrapper(Shape.GENERIC, className);
    }

    /**
     * The kind of wrapper.
     */
    public enum Shape {
        /** The type itself. */
        PLAIN,
        /** A {@code suspend} function. */
        SUSPEND,
        /** A generic class around the type. */
        GENERIC
    }
}
