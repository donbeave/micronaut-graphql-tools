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
package io.micronaut.graphql.tools.resolver;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

/**
 * Marks a resolver contract, or a resolver class, whose methods bind the fields of the subscription root type only. The root type is
 * the one that the schema names for the subscription operation, so a method of the implementation never binds a field of another
 * operation type.
 *
 * <p>A contract is an interface that extends this one and declares the methods, usually one per field. Only the methods
 * that implement a contract method are candidates for binding: a public helper method of the implementation binds nothing
 * and does not trip strict mode. A class that implements this interface directly, with no contract interface, binds all
 * its public methods, as a class that carries {@link GraphQLRootResolver} does.</p>
 *
 * <p>The interface carries no bean stereotype: the implementation is a bean when it has its own scope annotation, for
 * example {@code @Singleton}. A fake or a subclass of a bean uses {@code @Replaces}. The compile-time processor
 * ({@code micronaut-graphql-tools-processor}) must be on the compiler path, because it records the operation in the
 * annotation metadata of the implementation.</p>
 */
@GraphQLRootResolver
public interface SubscriptionResolver {
}
