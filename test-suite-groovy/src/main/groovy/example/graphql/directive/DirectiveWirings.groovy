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
package example.graphql.directive

import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLSchema
import graphql.schema.idl.SchemaDirectiveWiring
import graphql.schema.idl.SchemaDirectiveWiringEnvironment
import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.graphql.tools.customizer.GraphQLSchemaCustomizer
import io.micronaut.graphql.tools.directive.SchemaDirective
import jakarta.inject.Singleton

import java.util.function.Consumer

@CompileStatic
@Factory
class DirectiveWirings {

    @Bean
    @Singleton
    SchemaDirective upper() {
        SchemaDirective.named('upper', new UpperWiring())
    }

    @Bean
    @Singleton
    SchemaDirective tagged() {
        SchemaDirective.everyElement(['tagged'], new TaggedWiring())
    }

    @Bean
    @Singleton
    GraphQLSchemaCustomizer audit() {
        new AuditCustomizer()
    }

    static class AuditCustomizer implements GraphQLSchemaCustomizer {
        @Override
        GraphQLSchema customize(GraphQLSchema schema) {
            schema.getSchemaAppliedDirective('audit') == null ? schema : schema.transform(new Describe())
        }
    }

    static class UpperWiring implements SchemaDirectiveWiring {
        @Override
        GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> environment) {
            DataFetcher<?> original = environment.fieldDataFetcher
            environment.setFieldDataFetcher(new UpperFetcher(original))
        }
    }

    static class TaggedWiring implements SchemaDirectiveWiring {
        @Override
        GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> environment) {
            if (!environment.containsDirective('tagged')) {
                return environment.element
            }
            String label = environment.getAppliedDirective('tagged').getArgument('label').getValue()
            DataFetcher<?> original = environment.fieldDataFetcher
            environment.setFieldDataFetcher(new TaggedFetcher(label, original))
        }
    }

    static class Describe implements Consumer<GraphQLSchema.Builder> {
        @Override
        void accept(GraphQLSchema.Builder builder) {
            builder.description('audited')
        }
    }

    // Static classes instead of closures coerced to SAM types: that coercion needs Groovy's dynamic runtime, which a native image lacks.
    static class UpperFetcher implements DataFetcher<String> {
        private final DataFetcher<?> original

        UpperFetcher(DataFetcher<?> original) {
            this.original = original
        }

        @Override
        String get(DataFetchingEnvironment fetching) throws Exception {
            String.valueOf(original.get(fetching)).toUpperCase(Locale.ROOT)
        }
    }

    static class TaggedFetcher implements DataFetcher<String> {
        private final String label
        private final DataFetcher<?> original

        TaggedFetcher(String label, DataFetcher<?> original) {
            this.label = label
            this.original = original
        }

        @Override
        String get(DataFetchingEnvironment fetching) throws Exception {
            '[' + label + '] ' + original.get(fetching)
        }
    }
}
