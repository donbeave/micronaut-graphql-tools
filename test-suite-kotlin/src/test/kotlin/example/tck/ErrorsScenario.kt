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
package example.tck

import jakarta.inject.Singleton
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

// The fixtures of the TCK scenario `errors`.

@GraphQLType
class Parent {
    val name: String get() = "parent"
    val broken: String get() = throw IllegalStateException("boom")
}

@Singleton
@GraphQLRootResolver
@Requires(property = "tck.scenario", value = "errors")
class ErrorsQueries {

    fun ok(): String = "fine"

    fun failNullable(): String? = throw IllegalStateException("boom")

    fun failNonNull(): String = throw IllegalStateException("boom")

    fun failFuture(): CompletionStage<String?> = CompletableFuture.failedFuture(IllegalStateException("async boom"))

    fun nested(): Parent? = Parent()

    fun nestedNonNull(): Parent = Parent()

    fun echo(value: Int): Int = value
}
