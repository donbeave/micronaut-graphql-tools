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
/**
 * The compile-time checks of the GraphQL client as a Micronaut {@code TypeElementVisitor}: it runs under javac, KSP, kapt and
 * the Groovy compiler, parses and assembles the document of each {@code @GraphQLOperation} method, validates it against the
 * client schema and writes the result into the annotation metadata of the method. The rules live in
 * {@code graphql-tools-binding}; this package is the adapter over the {@code ClassElement} model. {@code GraphQLClientVisitor}
 * is the one public type, loaded by the compiler and never imported.
 */
package io.micronaut.graphql.tools.visitor.client;
