package example;

import io.micronaut.context.annotation.Factory;
import io.micronaut.graphql.tools.customizer.SchemaMappingDictionaryCustomizer;
import jakarta.inject.Singleton;

@Factory
public class TypeDictionary {

    @Singleton
    SchemaMappingDictionaryCustomizer dictionary() {
        return dictionary -> dictionary.registerType("Magazine", MagazineRecord.class);
    }

}
