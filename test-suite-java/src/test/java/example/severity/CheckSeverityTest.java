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
package example.severity;

import graphql.schema.GraphQLSchema;
import io.micronaut.context.ApplicationContext;
import io.micronaut.graphql.tools.exceptions.SchemaBindingException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Runs on the JVM and as a native binary (nativeTest): the schema has a type that no field returns, and the severity
// settings decide whether the startup fails.
class CheckSeverityTest {

    private static final String PREFIX = "micronaut.graphql.tools.";

    private static GraphQLSchema start(Map<String, Object> properties) {
        try (ApplicationContext context = ApplicationContext.run(properties, SeverityFactory.ENVIRONMENT)) {
            return context.getBean(GraphQLSchema.class);
        }
    }

    private static void assertFails(Map<String, Object> properties, String code) {
        Throwable e = assertThrows(Exception.class, () -> start(properties));
        while (!(e instanceof SchemaBindingException) && e.getCause() != null && e.getCause() != e) {
            e = e.getCause();
        }
        assertTrue(e instanceof SchemaBindingException, e.toString());
        assertTrue(e.getMessage().startsWith("[" + code + "]"), e.getMessage());
    }

    @Test
    void anUnreachableTypeFailsTheStartupByDefault() {
        assertFails(Map.of(), "unreachable-type");
    }

    @Test
    void warnIgnoreAndStrictFalseLetTheStartupGoOn() {
        assertNotNull(start(Map.of(PREFIX + "checks.unreachable-type", "warn")));
        assertNotNull(start(Map.of(PREFIX + "checks.unreachable-type", "ignore")));
        assertNotNull(start(Map.of(PREFIX + "strict", false)));
    }

    @Test
    void anExplicitFailWinsOverStrictFalse() {
        assertFails(Map.of(PREFIX + "strict", false, PREFIX + "checks.unreachable-type", "fail"), "unreachable-type");
    }

    @Test
    void anAllowedTypeAndAnAllowedManifestLineAcceptTheFinding() {
        assertNotNull(start(Map.of(PREFIX + "allow.unreachable", List.of("Orphan"))));
        assertNotNull(start(Map.of(PREFIX + "allow.findings", List.of("!unreachable-type Orphan"))));
    }

    @Test
    void aStaleAllowEntryFailsTheStartup() {
        assertFails(Map.of(PREFIX + "allow.unreachable", List.of("Orphan"),
                PREFIX + "allow.findings", List.of("!unreachable-type Missing")), "invalid-allow-findings");
    }

    @Test
    void aRemovedKeyAndAnUnknownCheckFailTheStartup() {
        assertFails(Map.of(PREFIX + "unreachable-types", "ignore"), "removed-configuration-key");
        assertFails(Map.of(PREFIX + "checks.no-such-check", "warn"), "invalid-check-setting");
    }

}
