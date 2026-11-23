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
package example.models;

import example.external.Atlas;
import example.external.ExternalModel;
import example.external.Region;
import example.external.generated.Axis;
import example.external.generated.Chart;
import example.external.unlisted.Holder;
import example.external.unlisted.Unlisted;
import graphql.schema.idl.SchemaParser;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.annotation.GraphQLModels;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.customizer.GraphQLTypeDefinitionRegistryCustomizer;
import jakarta.inject.Singleton;

import java.util.List;

// Model classes that carry no annotation (#68): the classes of another module or of a generator.
@Factory
public class ExternalModelFixtures {

    // the classes are listed
    public static final String CLASSES = "external-classes";
    // the package of the classes is listed
    public static final String PACKAGE = "external-package";
    // a returned class that nothing lists
    public static final String MISSING = "external-missing";
    // a class that a listed class reaches through a property, and that nothing lists
    public static final String NESTED = "external-nested";

    @Bean
    @Singleton
    @Requires(env = CLASSES)
    GraphQLTypeDefinitionRegistryCustomizer classesSchema() {
        return registry -> registry.merge(new SchemaParser().parse(
                "type Atlas { title: String! region: Region! pages: Int! }\n"
                        + "type Region { name: String! }\n"
                        + "extend type Query { atlas: Atlas atlases: [Atlas!]! }\n"));
    }

    @Bean
    @Singleton
    @Requires(env = PACKAGE)
    GraphQLTypeDefinitionRegistryCustomizer packageSchema() {
        return registry -> registry.merge(new SchemaParser().parse(
                "type Chart { label: String! axis: Axis! }\n"
                        + "type Axis { unit: String! }\n"
                        + "extend type Query { chart: Chart }\n"));
    }

    @Bean
    @Singleton
    @Requires(env = MISSING)
    GraphQLTypeDefinitionRegistryCustomizer missingSchema() {
        return registry -> registry.merge(new SchemaParser().parse(
                "type Unlisted { name: String! }\n"
                        + "extend type Query { unlisted: Unlisted }\n"));
    }

    @Bean
    @Singleton
    @Requires(env = NESTED)
    GraphQLTypeDefinitionRegistryCustomizer nestedSchema() {
        return registry -> registry.merge(new SchemaParser().parse(
                "type Holder { label: String! inner: Inner! }\n"
                        + "type Inner { name: String! }\n"
                        + "extend type Query { holder: Holder }\n"));
    }

    // tag::listed-classes[]
    @GraphQLModels(classes = {Atlas.class, Region.class})
    public static class AtlasModels {
    }
    // end::listed-classes[]

    // tag::listed-package[]
    @GraphQLModels(packages = "example.external.generated", includedAnnotations = ExternalModel.class)
    public static class GeneratedModels {
    }
    // end::listed-package[]

    @GraphQLModels(classes = Holder.class)
    public static class HolderModels {
    }

    // tag::resolver[]
    @Singleton
    @Requires(env = CLASSES)
    @GraphQLRootResolver
    public static class AtlasQuery {

        public Atlas atlas() {
            return new Atlas("World", new Region("Europe"), 320);
        }

        public List<Atlas> atlases() {
            return List.of(new Atlas("World", new Region("Europe"), 320), new Atlas("Roads", new Region("Asia"), 96));
        }

    }
    // end::resolver[]

    @Singleton
    @Requires(env = PACKAGE)
    @GraphQLRootResolver
    public static class ChartQuery {

        public Chart chart() {
            return new Chart("Sales", new Axis("EUR"));
        }

    }

    @Singleton
    @Requires(env = MISSING)
    @GraphQLRootResolver
    public static class UnlistedQuery {

        public Unlisted unlisted() {
            return new Unlisted();
        }

    }

    @Singleton
    @Requires(env = NESTED)
    @GraphQLRootResolver
    public static class HolderQuery {

        public Holder holder() {
            return new Holder();
        }

    }

}
