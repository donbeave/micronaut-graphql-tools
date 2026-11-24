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
package example.models

import example.external.Atlas
import example.external.ExternalModel
import example.external.Region
import example.external.generated.Axis
import example.external.generated.Chart
import example.external.unlisted.Holder
import example.external.unlisted.Unlisted
import graphql.schema.idl.SchemaParser
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLModels
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.customizer.GraphQLTypeDefinitionRegistryCustomizer
import jakarta.inject.Singleton

// Model classes that carry no annotation (#68): the classes of another module or of a generator.

// the classes are listed
const val EXTERNAL_CLASSES = "external-classes"

// the package of the classes is listed
const val EXTERNAL_PACKAGE = "external-package"

// a returned class that nothing lists
const val EXTERNAL_MISSING = "external-missing"

// a class that a listed class reaches through a property, and that nothing lists
const val EXTERNAL_NESTED = "external-nested"

@Factory
class ExternalModelFixtures {

    @Bean
    @Singleton
    @Requires(env = [EXTERNAL_CLASSES])
    fun classesSchema() = GraphQLTypeDefinitionRegistryCustomizer { registry ->
        registry.merge(
            SchemaParser().parse(
                "type Atlas { title: String! region: Region! pages: Int! }\n" +
                    "type Region { name: String! }\n" +
                    "extend type Query { atlas: Atlas atlases: [Atlas!]! }\n"
            )
        )
    }

    @Bean
    @Singleton
    @Requires(env = [EXTERNAL_PACKAGE])
    fun packageSchema() = GraphQLTypeDefinitionRegistryCustomizer { registry ->
        registry.merge(
            SchemaParser().parse(
                "type Chart { label: String! axis: Axis! }\n" +
                    "type Axis { unit: String! }\n" +
                    "extend type Query { chart: Chart }\n"
            )
        )
    }

    @Bean
    @Singleton
    @Requires(env = [EXTERNAL_MISSING])
    fun missingSchema() = GraphQLTypeDefinitionRegistryCustomizer { registry ->
        registry.merge(
            SchemaParser().parse(
                "type Unlisted { name: String! }\n" +
                    "extend type Query { unlisted: Unlisted }\n"
            )
        )
    }

    @Bean
    @Singleton
    @Requires(env = [EXTERNAL_NESTED])
    fun nestedSchema() = GraphQLTypeDefinitionRegistryCustomizer { registry ->
        registry.merge(
            SchemaParser().parse(
                "type Holder { label: String! inner: Inner! }\n" +
                    "type Inner { name: String! }\n" +
                    "extend type Query { holder: Holder }\n"
            )
        )
    }
}

// tag::listed-classes[]
@GraphQLModels(classes = [Atlas::class, Region::class])
class AtlasModels
// end::listed-classes[]

// tag::listed-package[]
@GraphQLModels(packages = ["example.external.generated"], includedAnnotations = [ExternalModel::class])
class GeneratedModels
// end::listed-package[]

@GraphQLModels(classes = [Holder::class])
class HolderModels

// tag::resolver[]
@Singleton
@Requires(env = [EXTERNAL_CLASSES])
@GraphQLRootResolver
class AtlasQuery {

    fun atlas(): Atlas = Atlas("World", Region("Europe"), 320)

    fun atlases(): List<Atlas> = listOf(Atlas("World", Region("Europe"), 320), Atlas("Roads", Region("Asia"), 96))
}
// end::resolver[]

@Singleton
@Requires(env = [EXTERNAL_PACKAGE])
@GraphQLRootResolver
class ChartQuery {

    fun chart(): Chart = Chart("Sales", Axis("EUR"))
}

@Singleton
@Requires(env = [EXTERNAL_MISSING])
@GraphQLRootResolver
class UnlistedQuery {

    fun unlisted(): Unlisted = Unlisted()
}

@Singleton
@Requires(env = [EXTERNAL_NESTED])
@GraphQLRootResolver
class HolderQuery {

    fun holder(): Holder = Holder()
}
