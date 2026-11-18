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
package io.micronaut.graphql.tools.schema;

import graphql.TrivialDataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanProperty;

/**
 * Reads one field from the {@link BeanProperty} that the binding resolved for it at startup: no lookup by name and no
 * {@code Optional} per request.
 *
 * @author Alexey Zhokhov
 */
@Internal
public final class MicronautPropertyDataFetcher implements TrivialDataFetcher<Object> {

    private final BeanProperty<Object, Object> property;

    public MicronautPropertyDataFetcher(BeanProperty<Object, Object> property) {
        this.property = property;
    }

    @Override
    public Object get(DataFetchingEnvironment environment) {
        return property.get(environment.getSource());
    }

}
