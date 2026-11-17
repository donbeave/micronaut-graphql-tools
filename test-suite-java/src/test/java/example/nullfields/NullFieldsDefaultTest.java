package example.nullfields;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

@MicronautTest
class NullFieldsDefaultTest extends AbstractNullFieldsTest {

    @Override
    String expectedMapperClassName() {
        return "io.micronaut.jackson.databind.JacksonDatabindMapper";
    }

}
