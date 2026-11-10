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

import graphql.schema.GraphQLSchema;
import io.micronaut.core.annotation.NonNull;

/**
 * Changes the {@link GraphQLSchema} that the library generated, for example with
 * {@link GraphQLSchema#transform(java.util.function.Consumer)} or {@link graphql.schema.SchemaTransformer}.
 *
 * <p>Every bean of this type is called once; each one receives the schema the previous one returned, and the last
 * result is the schema the library runs. The beans run lowest order first: by
 * {@link io.micronaut.core.annotation.Order @Order} on the bean class or factory method, or by
 * {@link io.micronaut.core.order.Ordered#getOrder()} if the bean implements {@code Ordered}. The library does not
 * check what a schema customizer changes: it is the last step, and the escape hatch for what the other customizers
 * cannot express.</p>
 */
@FunctionalInterface
public interface GraphQLSchemaCustomizer {

    /**
     * Returns the changed schema.
     *
     * @param schema the schema so far
     * @return the changed schema, or {@code schema} itself; never {@code null}
     */
    @NonNull
    GraphQLSchema customize(@NonNull GraphQLSchema schema);

}
