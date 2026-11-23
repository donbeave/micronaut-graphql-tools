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
package example.replaced;

import io.micronaut.configuration.graphql.GraphQLInvocation;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.exceptions.ConfigurationException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InvocationLimitsTest {

    private static final Map<String, Object> PORT = Map.of("micronaut.server.port", -1);

    @Test
    void anInvocationThatIsOnlySecondaryFailsTheStartupWithTheWayOut() {
        Throwable failure = assertThrows(Throwable.class, () -> ApplicationContext.run(PORT, InvocationLimitsFactory.SECONDARY));

        String message = configuration(failure).getMessage();
        assertTrue(message.contains("@Secondary"), message);
        assertTrue(message.contains("Fix: remove @Secondary"), message);
    }

    @Test
    void aSchemaTemplateOverEveryInvocationFailsTheStartupWithTheWayOut() {
        Throwable failure = assertThrows(Throwable.class, () -> ApplicationContext.run(PORT, InvocationLimitsFactory.EACH_INVOCATION));

        String message = configuration(failure).getMessage();
        assertTrue(message.contains("@EachBean(GraphQLInvocation.class)"), message);
        assertTrue(message.contains("target the application's own bean type"), message);
    }

    @Test
    void aFactoryDefaultReplacedByQualifierDoesNotDisableTheLibraryInvocation() {
        try (ApplicationContext context = ApplicationContext.run(
                Map.of("micronaut.server.port", -1, "micronaut.graphql.tools.eager-init", false), InvocationLimitsFactory.QUALIFIED_DEFAULT)) {
            Set<String> types = context.getBeanDefinitions(GraphQLInvocation.class).stream()
                    .map(definition -> definition.getBeanType().getSimpleName())
                    .collect(Collectors.toSet());

            assertTrue(types.contains("LibraryGraphQLInvocation"), types.toString());
            assertFalse(types.contains("DefaultGraphQLInvocation"), types.toString());
        }
    }

    private static ConfigurationException configuration(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof ConfigurationException configuration) {
                return configuration;
            }
        }
        throw new AssertionError("no ConfigurationException", failure);
    }

}
