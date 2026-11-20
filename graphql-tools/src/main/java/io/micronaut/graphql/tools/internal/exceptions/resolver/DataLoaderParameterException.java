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
package io.micronaut.graphql.tools.internal.exceptions.resolver;

import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.internal.exceptions.AbstractMappingException;
import io.micronaut.graphql.tools.mapping.MappingContext;

/**
 * A {@code DataLoader<K, V>} parameter of a resolver method cannot be served: no batch loader bean has its types
 * ({@link BindingCode#DATA_LOADER_NOT_FOUND}), or the application replaced the library's invocation, which provides the
 * loaders ({@link BindingCode#EXECUTION_CONTEXT_UNAVAILABLE}).
 */
@Internal
public final class DataLoaderParameterException extends AbstractMappingException {

    public DataLoaderParameterException(BindingCode code, String message, MappingContext mappingContext) {
        super(code, message, mappingContext);
    }

}
