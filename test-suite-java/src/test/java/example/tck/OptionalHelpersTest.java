package example.tck;

import io.micronaut.graphql.tools.ArgumentValue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OptionalHelpersTest {

    @Test
    void onlyAProvidedNonNullValueIsPresent() {
        assertEquals("absent", ArgumentsScenario.describePresence(ArgumentValue.omitted()));
        assertEquals("absent", ArgumentsScenario.describePresence(ArgumentValue.ofNullable(null)));
        assertEquals("present:a", ArgumentsScenario.describePresence(ArgumentValue.ofNullable("a")));

        List<String> seen = new ArrayList<>();
        ArgumentsScenario.rememberIfPresent(ArgumentValue.omitted(), seen);
        ArgumentsScenario.rememberIfPresent(ArgumentValue.ofNullable(null), seen);
        ArgumentsScenario.rememberIfPresent(ArgumentValue.ofNullable("a"), seen);
        assertEquals(List.of("a"), seen);
    }

}
