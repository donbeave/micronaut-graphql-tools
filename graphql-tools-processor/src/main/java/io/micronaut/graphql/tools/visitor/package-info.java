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
 * The compile-time checks as a Micronaut {@code TypeElementVisitor}: one implementation that runs under javac, KSP, kapt
 * and the Groovy compiler. The rules live in {@code graphql-tools-binding}; this package is the adapter over the
 * {@code ClassElement} model, the option handling and the diagnostics. The visitor is isolating, keeps its state per
 * compilation and reports only what a class and the schema decide. {@code GraphQLToolsVisitor} is the one public type, loaded
 * by the compiler and never imported; {@code BindingRulesAccess} is {@code @Internal}, for the benchmarks of this repository.
 */
package io.micronaut.graphql.tools.visitor;
