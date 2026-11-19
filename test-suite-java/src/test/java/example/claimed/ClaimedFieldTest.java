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
package example.claimed;

import graphql.ExecutionResult;
import graphql.GraphQL;
import io.micronaut.graphql.tools.manifest.BindingManifest;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(environments = ClaimedFieldCustomizers.ENVIRONMENT)
class ClaimedFieldTest {

    @Inject
    GraphQL graphQL;

    @Inject
    BindingManifest manifest;

    @Test
    void aClaimedFieldIsServedByTheCustomizer() {
        ExecutionResult result = graphQL.execute("{ serverVersion allBooks { title } }");

        assertTrue(result.getErrors().isEmpty());
        Map<String, Object> data = result.getData();
        assertEquals("1.0", data.get("serverVersion"));
        assertEquals(5, ((java.util.List<?>) data.get("allBooks")).size());
    }

    @Test
    void aClaimedFieldIsListedInTheManifest() {
        assertTrue(manifest.getLines().stream().anyMatch(it -> it.startsWith("Query.serverVersion: String -> claimed ; ")));
    }

}
