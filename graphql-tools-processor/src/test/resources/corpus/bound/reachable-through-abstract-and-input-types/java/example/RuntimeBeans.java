package example;

import io.micronaut.context.annotation.Factory;
import io.micronaut.graphql.tools.SchemaMappingDictionaryCustomizer;
import jakarta.inject.Singleton;

@Factory
public class RuntimeBeans {

    @Singleton
    SchemaMappingDictionaryCustomizer dictionary() {
        return dictionary -> dictionary.registerType("Book", Book.class).registerType("Author", Author.class).registerType("Shelf", Shelf.class);
    }

}
