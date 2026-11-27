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
package io.micronaut.graphql.tools.codegen.golden;

import io.micronaut.graphql.tools.codegen.GeneratedFile;

import java.util.List;

/**
 * Compiles generated sources in memory with the compiler of their language. The emitters of the three languages implement
 * this: {@link JavaInMemoryCompiler} and {@link GroovyInMemoryCompiler} are here, and a Kotlin one only needs {@link #compile}.
 */
public interface InMemoryCompiler {

    /**
     * Compiles all files together. Nothing is written to disk.
     *
     * @param files the generated sources
     * @return the compiler errors; empty when the sources compile
     */
    List<String> compile(List<GeneratedFile> files);

    /**
     * Fails with the compiler errors when the files do not compile.
     *
     * @param files the generated sources
     */
    default void assertCompiles(List<GeneratedFile> files) {
        List<String> errors = compile(files);
        if (!errors.isEmpty()) {
            throw new AssertionError("The generated sources do not compile:\n" + String.join("\n", errors));
        }
    }
}
