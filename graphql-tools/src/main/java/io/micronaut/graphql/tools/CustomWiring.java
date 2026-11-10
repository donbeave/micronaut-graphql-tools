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
package io.micronaut.graphql.tools;

import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.schema.idl.SchemaDirectiveWiringEnvironment;

/**
 * A no-op {@link SchemaDirectiveWiring} that delegates every callback to the interface defaults.
 *
 * @author Alexey Zhokhov
 */
public class CustomWiring implements SchemaDirectiveWiring {

    @Override
    public GraphQLInputObjectField onInputObjectField(SchemaDirectiveWiringEnvironment<GraphQLInputObjectField> environment) {
        return SchemaDirectiveWiring.super.onInputObjectField(environment);
    }

    @Override
    public GraphQLInputObjectType onInputObjectType(SchemaDirectiveWiringEnvironment<GraphQLInputObjectType> environment) {
        return SchemaDirectiveWiring.super.onInputObjectType(environment);
    }

}
