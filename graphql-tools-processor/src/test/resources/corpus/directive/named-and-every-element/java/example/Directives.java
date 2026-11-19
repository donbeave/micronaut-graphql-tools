package example;

import graphql.schema.idl.SchemaDirectiveWiring;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.graphql.tools.directive.SchemaDirective;
import jakarta.inject.Singleton;
import java.util.List;

@Factory
public class Directives {

    public static class NoOp implements SchemaDirectiveWiring {
    }

    @Bean
    @Singleton
    public SchemaDirective audit() {
        return SchemaDirective.named("audit", new NoOp());
    }

    @Bean
    @Singleton
    public SchemaDirective everyElement() {
        return SchemaDirective.everyElement(List.of("audit"), new NoOp());
    }

}
