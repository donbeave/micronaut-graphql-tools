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
 * The failures of the binding at startup. Every failure is a {@link io.micronaut.graphql.tools.exceptions.SchemaBindingException};
 * the subclasses name one failure each, and each carries a stable error code. The guide page "Exceptions" is the
 * error reference.
 *
 * <p><b>Audience.</b> Mixed. The exceptions are public API, but only {@code SchemaBindingException} is meant to be caught: match on its code,
 * not on a subclass. The subclasses are being made internal and the package is being reduced to the one exception
 * (see the guide page "API Map").</p>
 */
package io.micronaut.graphql.tools.exceptions;
