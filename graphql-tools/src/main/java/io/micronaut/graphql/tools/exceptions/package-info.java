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
 * The failure of the binding at startup: {@link io.micronaut.graphql.tools.exceptions.SchemaBindingException}, the one
 * exception to catch. It carries a stable error code ({@link io.micronaut.graphql.tools.binding.BindingCode}); the guide
 * page "Exceptions" is the error reference.
 *
 * <p><b>Audience.</b> Public API. Match on {@code SchemaBindingException#code()}. The classes that build each failure are
 * internal.</p>
 */
package io.micronaut.graphql.tools.exceptions;
