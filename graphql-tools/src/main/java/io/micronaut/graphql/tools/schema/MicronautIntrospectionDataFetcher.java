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

import graphql.TrivialDataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;

/**
 * @author Alexey Zhokhov
 */
@Internal
public final class MicronautIntrospectionDataFetcher implements TrivialDataFetcher<Object> {

    private final BeanIntrospection<Object> beanIntrospection;

    public MicronautIntrospectionDataFetcher(BeanIntrospection<Object> beanIntrospection) {
        this.beanIntrospection = beanIntrospection;
    }

    @Override
    public Object get(DataFetchingEnvironment environment) {
        String fieldName = environment.getField().getName();

        BeanProperty<?, Object> property = beanIntrospection.getProperty(fieldName).get();

        return property.get(environment.getSource());
    }

}
