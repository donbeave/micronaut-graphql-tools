package example.nullfields;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

@MicronautTest
@Property(name = "jackson.serialization-inclusion", value = "NON_NULL")
class NullFieldsNonNullInclusionTest extends AbstractNullFieldsTest {

    @Override
    String expectedMapperClassName() {
        return "io.micronaut.jackson.databind.JacksonDatabindMapper";
    }

}
