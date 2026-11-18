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
package example.manifest;

import io.micronaut.graphql.tools.BindingManifest;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(startApplication = false)
class BindingManifestTest {

    @Inject
    BindingManifest bindingManifest;

    @Test
    void theManifestOfTheSampleEqualsTheGoldenFile() throws IOException {
        try (InputStream golden = getClass().getResourceAsStream("/binding-manifest.txt")) {
            assertEquals(new String(golden.readAllBytes(), StandardCharsets.UTF_8),
                    String.join("\n", bindingManifest.getLines()) + "\n");
        }
    }

    @Test
    void aResolverOverridingObjectMethodsHasNoUnboundFinding() {
        assertTrue(bindingManifest.getLines().stream().noneMatch(it -> it.startsWith("!unbound-resolver-method ")
                && (it.contains("toString") || it.contains("equals") || it.contains("hashCode"))),
                String.valueOf(bindingManifest.getLines()));
    }

}
