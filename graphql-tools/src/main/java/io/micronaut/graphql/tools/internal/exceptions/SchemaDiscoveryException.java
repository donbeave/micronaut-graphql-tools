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
package io.micronaut.graphql.tools.internal.exceptions;

import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.exceptions.SchemaBindingException;

/**
 * The schema files cannot be found, read, parsed or merged. The code is {@link BindingCode#SCHEMA_NOT_FOUND} for a
 * location that matches nothing or is invalid, and {@link BindingCode#SCHEMA_INVALID} for a file that does not parse or
 * conflicts with another. The message names the file or the locations.
 */
@Internal
public final class SchemaDiscoveryException extends SchemaBindingException {

    public SchemaDiscoveryException(BindingCode code, String message) {
        super(code, message);
    }

    public SchemaDiscoveryException(BindingCode code, String message, Throwable cause) {
        super(code, message, cause);
    }

}
