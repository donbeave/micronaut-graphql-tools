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

import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;

/**
 * Marks a resolver contract, or a resolver class, whose methods bind fields of the object type that {@code T} is mapped to.
 * The first parameter of each method is the source object of type {@code T}, so no class literal is needed:
 * {@code interface BookTitleResolver extends TypeResolver<Book>}.
 *
 * <p>A contract is an interface that extends this one and declares the methods, usually one per field. Only the methods
 * that implement a contract method are candidates for binding: a public helper method of the implementation binds nothing
 * and does not trip strict mode. A class that implements this interface directly, with no contract interface, binds all
 * its public methods, as a class that carries {@link GraphQLTypeResolver} does. A class can combine this marker with
 * {@link GraphQLTypeResolver} contracts of other source types.</p>
 *
 * <p>The interface carries no bean stereotype: the implementation is a bean when it has its own scope annotation, for
 * example {@code @Singleton}. A fake or a subclass of a bean uses {@code @Replaces}. The compile-time processor
 * ({@code micronaut-graphql-tools-processor}) must be on the compiler path, because it reads {@code T} from the generic
 * metadata of the implementation and records it in its annotation metadata.</p>
 *
 * @param <T> the source type, the class that the schema type is mapped to
 */
@GraphQLTypeResolver
public interface TypeResolver<T> {
}
