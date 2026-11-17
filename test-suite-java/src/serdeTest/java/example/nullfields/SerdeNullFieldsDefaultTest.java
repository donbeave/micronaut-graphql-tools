package example.nullfields;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

@MicronautTest
class SerdeNullFieldsDefaultTest extends AbstractNullFieldsTest {

    @Override
    String expectedMapperClassName() {
        return "io.micronaut.serde.jackson.JacksonJsonMapper";
    }

}
