/*
 * Copyright 2021-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package example.tck;

import io.micronaut.graphql.tools.input.ArgumentValue;
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
