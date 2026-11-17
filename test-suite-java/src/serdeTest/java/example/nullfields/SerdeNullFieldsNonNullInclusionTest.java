package example.nullfields;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

@MicronautTest
@Property(name = "micronaut.serde.serialization.inclusion", value = "NON_NULL")
class SerdeNullFieldsNonNullInclusionTest extends AbstractNullFieldsTest {

    @Override
    String expectedMapperClassName() {
        return "io.micronaut.serde.jackson.JacksonJsonMapper";
    }

}
