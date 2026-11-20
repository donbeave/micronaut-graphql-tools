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
import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.customizer.TypeDefinitionRegistryCustomizer
import io.micronaut.scheduling.annotation.ExecuteOn
import jakarta.inject.Singleton

// The two startup failures of @ExecuteOn (#427): an executor name that matches no bean, and @ExecuteOn on a property.
@CompileStatic
@Factory
class ExecuteOnFixtures {

    // @ExecuteOn names an executor bean that does not exist
    static final String UNKNOWN = 'execute-on-unknown'
    // @ExecuteOn on the getter that binds a property
    static final String PROPERTY = 'execute-on-property'

    @Bean
    @Singleton
    @Requires(env = ExecuteOnFixtures.UNKNOWN)
    TypeDefinitionRegistryCustomizer unknownSchema() {
        return { registry ->
            registry.merge(new SchemaParser().parse('extend type Query { slowLookup: String! }\n'))
        } as TypeDefinitionRegistryCustomizer
    }

    @Bean
    @Singleton
    @Requires(env = ExecuteOnFixtures.PROPERTY)
    TypeDefinitionRegistryCustomizer propertySchema() {
        return { registry ->
            registry.merge(new SchemaParser().parse('type Gadget { size: String! }\nextend type Query { gadget: Gadget }\n'))
        } as TypeDefinitionRegistryCustomizer
    }
}

@Singleton
@CompileStatic
@GraphQLRootResolver
@Requires(env = ExecuteOnFixtures.UNKNOWN)
class ExecuteOnUnknownQueries {

    @ExecuteOn('no-such-executor')
    String slowLookup() {
        return 'never'
    }
}

@Singleton
@CompileStatic
@GraphQLRootResolver
@Requires(env = ExecuteOnFixtures.PROPERTY)
class ExecuteOnPropertyQueries {

    Gadget gadget() {
        return new Gadget()
    }
}

@CompileStatic
@GraphQLType
class Gadget {

    @ExecuteOn('io')
    String getSize() {
        return 'small'
    }
}
