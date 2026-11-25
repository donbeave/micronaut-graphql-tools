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
package example.graphql.directive;

import graphql.schema.DataFetcher;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.schema.idl.SchemaDirectiveWiringEnvironment;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.graphql.tools.customizer.GraphQLSchemaCustomizer;
import io.micronaut.graphql.tools.directive.SchemaDirective;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Locale;

// tag::directives[]
@Factory
public class DirectiveWirings {

    @Bean
    @Singleton
    SchemaDirective upper() {
        return SchemaDirective.named("upper", new UpperWiring());
    }

    @Bean
    @Singleton
    SchemaDirective tagged() {
        return SchemaDirective.everyElement(List.of("tagged"), new TaggedWiring());
    }

    @Bean
    @Singleton
    GraphQLSchemaCustomizer audit() {
        return schema -> schema.getSchemaAppliedDirective("audit") == null
                ? schema
                : schema.transform(builder -> builder.description("audited"));
    }

    static final class UpperWiring implements SchemaDirectiveWiring {
        @Override
        public GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> environment) {
            DataFetcher<?> original = environment.getFieldDataFetcher();
            return environment.setFieldDataFetcher(fetching -> String.valueOf(original.get(fetching)).toUpperCase(Locale.ROOT));
        }
    }

    static final class TaggedWiring implements SchemaDirectiveWiring {
        @Override
        public GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> environment) {
            if (!environment.containsDirective("tagged")) {
                return environment.getElement();
            }
            String label = environment.getAppliedDirective("tagged").getArgument("label").getValue();
            DataFetcher<?> original = environment.getFieldDataFetcher();
            return environment.setFieldDataFetcher(fetching -> "[" + label + "] " + original.get(fetching));
        }
    }

}
// end::directives[]
