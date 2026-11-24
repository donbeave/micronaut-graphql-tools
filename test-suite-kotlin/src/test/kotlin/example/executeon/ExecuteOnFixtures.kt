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
package example.executeon

import graphql.schema.idl.SchemaParser
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.customizer.GraphQLTypeDefinitionRegistryCustomizer
import io.micronaut.scheduling.annotation.ExecuteOn
import jakarta.inject.Singleton

// The two startup failures of @ExecuteOn (#427): an executor name that matches no bean, and @ExecuteOn on a property.

// @ExecuteOn names an executor bean that does not exist
const val EXECUTE_ON_UNKNOWN = "execute-on-unknown"

// @ExecuteOn on the getter that binds a property
const val EXECUTE_ON_PROPERTY = "execute-on-property"

@Factory
class ExecuteOnFixtures {

    @Bean
    @Singleton
    @Requires(env = [EXECUTE_ON_UNKNOWN])
    fun unknownSchema() = GraphQLTypeDefinitionRegistryCustomizer { registry ->
        registry.merge(SchemaParser().parse("extend type Query { slowLookup: String! }\n"))
    }

    @Bean
    @Singleton
    @Requires(env = [EXECUTE_ON_PROPERTY])
    fun propertySchema() = GraphQLTypeDefinitionRegistryCustomizer { registry ->
        registry.merge(SchemaParser().parse("type Gadget { size: String! }\nextend type Query { gadget: Gadget }\n"))
    }
}

@Singleton
@GraphQLRootResolver
@Requires(env = [EXECUTE_ON_UNKNOWN])
open class ExecuteOnUnknownQueries {

    @ExecuteOn("no-such-executor")
    open fun slowLookup(): String = "never"
}

@Singleton
@GraphQLRootResolver
@Requires(env = [EXECUTE_ON_PROPERTY])
open class ExecuteOnPropertyQueries {

    open fun gadget(): Gadget = Gadget()
}

@GraphQLType
class Gadget {

    val size: String
        @ExecuteOn("io") get() = "small"
}
