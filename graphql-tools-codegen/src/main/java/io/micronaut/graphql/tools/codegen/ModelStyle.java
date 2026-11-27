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
 * The shape of a generated model type, the value of the {@code modelStyle} option. Input types are records in every case.
 */
public enum ModelStyle {
    /** An immutable record (a data class in Kotlin). */
    RECORD,
    /** A mutable bean with a getter and a setter for each field. */
    BEAN
}
