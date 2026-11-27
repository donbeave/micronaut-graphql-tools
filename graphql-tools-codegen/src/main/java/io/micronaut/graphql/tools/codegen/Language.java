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
package io.micronaut.graphql.tools.codegen;

/**
 * The language of the generated sources, the value of the {@code language} option.
 */
public enum Language {
    /** Java 17 sources: records, sealed interfaces. */
    JAVA,
    /** Kotlin sources: data classes, sealed interfaces. */
    KOTLIN,
    /** Groovy sources: the Java output with {@code @CompileStatic}. */
    GROOVY
}
