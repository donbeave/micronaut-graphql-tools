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
import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLModels
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.customizer.TypeDefinitionRegistryCustomizer
import jakarta.inject.Singleton

// Model classes that carry no annotation (#68): the classes of another module or of a generator.
@CompileStatic
@Factory
class ExternalModelFixtures {

    // the classes are listed
    static final String CLASSES = 'external-classes'
    // the package of the classes is listed
    static final String PACKAGE = 'external-package'
    // a returned class that nothing lists
    static final String MISSING = 'external-missing'
    // a class that a listed class reaches through a property, and that nothing lists
    static final String NESTED = 'external-nested'

    @Bean
    @Singleton
    @Requires(env = ExternalModelFixtures.CLASSES)
    TypeDefinitionRegistryCustomizer classesSchema() {
        return { registry ->
            registry.merge(new SchemaParser().parse(
                    'type Atlas { title: String! region: Region! pages: Int! }\n' +
                            'type Region { name: String! }\n' +
                            'extend type Query { atlas: Atlas atlases: [Atlas!]! }\n'))
        } as TypeDefinitionRegistryCustomizer
    }

    @Bean
    @Singleton
    @Requires(env = ExternalModelFixtures.PACKAGE)
    TypeDefinitionRegistryCustomizer packageSchema() {
        return { registry ->
            registry.merge(new SchemaParser().parse(
                    'type Chart { label: String! axis: Axis! }\n' +
                            'type Axis { unit: String! }\n' +
                            'extend type Query { chart: Chart }\n'))
        } as TypeDefinitionRegistryCustomizer
    }

    @Bean
    @Singleton
    @Requires(env = ExternalModelFixtures.MISSING)
    TypeDefinitionRegistryCustomizer missingSchema() {
        return { registry ->
            registry.merge(new SchemaParser().parse(
                    'type Unlisted { name: String! }\n' +
                            'extend type Query { unlisted: Unlisted }\n'))
        } as TypeDefinitionRegistryCustomizer
    }

    @Bean
    @Singleton
    @Requires(env = ExternalModelFixtures.NESTED)
    TypeDefinitionRegistryCustomizer nestedSchema() {
        return { registry ->
            registry.merge(new SchemaParser().parse(
                    'type Holder { label: String! inner: Inner! }\n' +
                            'type Inner { name: String! }\n' +
                            'extend type Query { holder: Holder }\n'))
        } as TypeDefinitionRegistryCustomizer
    }

    // tag::listed-classes[]
    @GraphQLModels(classes = [Atlas, Region])
    static class AtlasModels {
    }
    // end::listed-classes[]

    // tag::listed-package[]
    @GraphQLModels(packages = 'example.external.generated', includedAnnotations = ExternalModel)
    static class GeneratedModels {
    }
    // end::listed-package[]

    @GraphQLModels(classes = [Holder])
    static class HolderModels {
    }

    // tag::resolver[]
    @Requires(env = ExternalModelFixtures.CLASSES)
    @GraphQLRootResolver
    static class AtlasQuery {

        Atlas atlas() {
            return new Atlas(title: 'World', region: new Region(name: 'Europe'), pages: 320)
        }

        List<Atlas> atlases() {
            return [new Atlas(title: 'World', region: new Region(name: 'Europe'), pages: 320),
                    new Atlas(title: 'Roads', region: new Region(name: 'Asia'), pages: 96)]
        }

    }
    // end::resolver[]

    @Requires(env = ExternalModelFixtures.PACKAGE)
    @GraphQLRootResolver
    static class ChartQuery {

        Chart chart() {
            return new Chart(label: 'Sales', axis: new Axis(unit: 'EUR'))
        }

    }

    @Requires(env = ExternalModelFixtures.MISSING)
    @GraphQLRootResolver
    static class UnlistedQuery {

        Unlisted unlisted() {
            return new Unlisted()
        }

    }

    @Requires(env = ExternalModelFixtures.NESTED)
    @GraphQLRootResolver
    static class HolderQuery {

        Holder holder() {
            return new Holder()
        }

    }

}
