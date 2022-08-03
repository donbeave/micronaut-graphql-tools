/*
 * Copyright 2021-2022 original authors
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
package io.micronaut.graphql.tools.schema;

import graphql.schema.DataFetcher;
import graphql.schema.idl.FieldWiringEnvironment;
import graphql.schema.idl.WiringFactory;
import io.micronaut.core.annotation.Internal;

/**
 * @author Alexey Zhokhov
 */
@Internal
public final class DefaultWiringFactory implements WiringFactory {

    @Override
    public DataFetcher getDefaultDataFetcher(FieldWiringEnvironment environment) {
        throw new UnsupportedOperationException("Unprocessed type: " + environment.getParentType().getName());
    }

}
