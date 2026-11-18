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
package io.micronaut.graphql.tools.tck;

import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonDiffTest {

    private static List<String> diff(String expected, String actual) throws IOException {
        JsonMapper json = JsonMapper.createDefault();
        return JsonDiff.diff(json.readValue(expected, Argument.OBJECT_ARGUMENT), json.readValue(actual, Argument.OBJECT_ARGUMENT));
    }

    @Test
    void equalDocumentsHaveNoDifferenceWhateverTheKeyOrderAndNumberType() throws IOException {
        assertTrue(diff("{\"a\":1,\"b\":[1.5,{\"c\":null}]}", "{\"b\":[1.50,{\"c\":null}],\"a\":1.0}").isEmpty());
    }

    @Test
    void reportsMissingUnexpectedAndChangedValuesWithTheirPath() throws IOException {
        List<String> differences = diff("{\"data\":{\"a\":1,\"b\":\"x\",\"c\":[1,2]}}", "{\"data\":{\"a\":2,\"c\":[1],\"d\":true}}");
        assertEquals(List.of(
                "$.data.a: expected 1, found 2",
                "$.data.b: missing, expected \"x\"",
                "$.data.c: expected 2 elements, found 1",
                "$.data.d: unexpected, found true"), differences);
    }

    @Test
    void nullDoesNotEqualAnAbsentKeyOrAValue() throws IOException {
        assertEquals(1, diff("{\"a\":null}", "{}").size());
        assertEquals(1, diff("{\"a\":null}", "{\"a\":0}").size());
        assertEquals(1, diff("{\"a\":0}", "{\"a\":null}").size());
    }

    @Test
    void anyStringMatchesAnyStringButNothingElse() throws IOException {
        assertTrue(diff("{\"m\":\"@any\"}", "{\"m\":\"whatever\"}").isEmpty());
        assertEquals(1, diff("{\"m\":\"@any\"}", "{\"m\":1}").size());
        assertEquals(1, diff("{\"m\":\"@any\"}", "{\"m\":null}").size());
    }

}
