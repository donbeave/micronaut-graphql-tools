package example.graphql;

import example.graphql.type.SecurityError;
import example.graphql.type.ValidationError;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.graphql.tools.SchemaMappingDictionaryCustomizer;
import jakarta.inject.Singleton;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

@Factory
public class GraphQLFactory {

    // TODO use application.yml
    @Bean
    @Singleton
    public TypeDefinitionRegistry typeDefinitionRegistry(ResourceResolver resourceResolver) {
        InputStream inputStream = resourceResolver.getResourceAsStream("classpath:schema.graphqls").get();

        return new TypeDefinitionRegistry()
                .merge(new SchemaParser().parse(new BufferedReader(new InputStreamReader(inputStream))));
    }

    @Bean
    @Singleton
    public SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
        return schemaMappingDictionary -> schemaMappingDictionary
                .registerType("SecurityError", SecurityError.class)
                .registerType("ValidationError", ValidationError.class);
    }

}
