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

import io.micronaut.core.annotation.Internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * The accessors that the compile-time checks recorded in {@code META-INF/micronaut-graphql-tools/accessors.txt}: for
 * each property of an introspected type the getter that reads it, and for each {@code @GraphQLField} method the class it
 * is written in. Micronaut's bean introspection does not say which method reads a property, and the manifest must not
 * ask reflection, so it takes the answer from the compile-time metadata. Loaded when the manifest is rendered, once.
 *
 * <p>A line is {@code Type#property → Class#getter()} or {@code Type#method/arity → Class#method(parameter types)}.
 * A type the checks did not see has no line, and its members are rendered as {@code Type#property}.</p>
 */
@Internal
final class CompileTimeAccessors {

    static final String PATH = "META-INF/micronaut-graphql-tools/accessors.txt";

    private static final String ARROW = " → ";

    private CompileTimeAccessors() {
    }

    /**
     * @return the accessors of every module on the classpath, by {@code Type#member}
     */
    static Map<String, String> get() {
        return Holder.ACCESSORS;
    }

    static Map<String, String> load(ClassLoader classLoader) {
        Map<String, String> accessors = new HashMap<>();
        try {
            Enumeration<URL> resources = classLoader.getResources(PATH);
            while (resources.hasMoreElements()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        resources.nextElement().openStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        int arrow = line.indexOf(ARROW);
                        if (!line.startsWith("#") && arrow > 0) {
                            accessors.put(line.substring(0, arrow), line.substring(arrow + ARROW.length()));
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + PATH, e);
        }
        return accessors;
    }

    private static final class Holder {
        static final Map<String, String> ACCESSORS = load(CompileTimeAccessors.class.getClassLoader());
    }

}
