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
package example.executeon;

import graphql.schema.GraphQLSchema;
import io.micronaut.context.ApplicationContext;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.exceptions.SchemaBindingException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Runs on the JVM and as a native binary (nativeTest): the startup failures of @ExecuteOn.
class ExecuteOnFailureTest {

    private static SchemaBindingException failure(String environment) {
        Throwable e = assertThrows(Exception.class, () -> {
            try (ApplicationContext context = ApplicationContext.run(Map.of(), environment)) {
                context.getBean(GraphQLSchema.class);
            }
        });
        while (!(e instanceof SchemaBindingException) && e.getCause() != null && e.getCause() != e) {
            e = e.getCause();
        }
        assertTrue(e instanceof SchemaBindingException, e.toString());
        return (SchemaBindingException) e;
    }

    @Test
    void anUnknownExecutorFailsTheStartupAndListsTheExecutorBeans() {
        SchemaBindingException e = failure(ExecuteOnFixtures.UNKNOWN);
        assertEquals(BindingCode.UNKNOWN_EXECUTOR, e.getCode());
        assertTrue(e.getMessage().startsWith("[unknown-executor] ") && e.getMessage().contains("@ExecuteOn(\"no-such-executor\")") && e.getMessage().contains("Query.slowLookup"), e.getMessage());
        assertTrue(e.getMessage().contains("The executor beans are:") && e.getMessage().contains("io"), e.getMessage());
        assertTrue(e.getMessage().contains("micronaut.executors"), e.getMessage());
    }

    @Test
    void executeOnAPropertyFailsTheStartupAndSaysHowToFixIt() {
        SchemaBindingException e = failure(ExecuteOnFixtures.PROPERTY);
        assertEquals(BindingCode.EXECUTE_ON_PROPERTY, e.getCode());
        assertTrue(e.getMessage().startsWith("[execute-on-property] "), e.getMessage());
        assertTrue(e.getMessage().contains("@GraphQLField method")
                && e.getMessage().contains("type resolver"), e.getMessage());
    }
}
