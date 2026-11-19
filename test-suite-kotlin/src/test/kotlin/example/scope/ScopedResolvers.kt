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
package example.scope

import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import io.micronaut.context.annotation.Prototype
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.customizer.TypeDefinitionRegistryCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.runtime.http.scope.RequestScope
import jakarta.inject.Singleton
import java.util.concurrent.atomic.AtomicInteger

const val SCOPES_ENVIRONMENT = "resolver-scopes"

private val IDS = AtomicInteger()

@Singleton
@Requires(env = [SCOPES_ENVIRONMENT])
class ExtendQuery : TypeDefinitionRegistryCustomizer {
    override fun customize(registry: TypeDefinitionRegistry) {
        registry.merge(
            SchemaParser().parse("extend type Query { singletonId: Int! prototypeId: Int! requestId: Int! }"),
        )
    }
}

@Singleton
@GraphQLRootResolver
@Requires(env = [SCOPES_ENVIRONMENT])
class SingletonResolver {
    private val id = IDS.incrementAndGet()
    fun singletonId(): Int = id
}

@Prototype
@GraphQLRootResolver
@Requires(env = [SCOPES_ENVIRONMENT])
class PrototypeResolver {
    private val id = IDS.incrementAndGet()
    fun prototypeId(): Int = id
}

@RequestScope
@GraphQLRootResolver
@Requires(env = [SCOPES_ENVIRONMENT])
open class RequestResolver {
    private val id = IDS.incrementAndGet()
    open fun requestId(): Int = id
}
