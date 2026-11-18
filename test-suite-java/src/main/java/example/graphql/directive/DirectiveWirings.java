package example.graphql.directive;

import graphql.schema.DataFetcher;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.schema.idl.SchemaDirectiveWiringEnvironment;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.graphql.tools.GraphQLSchemaCustomizer;
import io.micronaut.graphql.tools.SchemaDirective;
import jakarta.inject.Singleton;

import java.util.List;

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
            return environment.setFieldDataFetcher(fetching -> String.valueOf(original.get(fetching)).toUpperCase());
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
