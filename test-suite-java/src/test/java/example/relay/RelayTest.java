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
package example.relay;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Runs on the JVM and as a native binary (nativeTest): a Relay connection that is bound to own record types, with strict mode
// at its default.
class RelayTest {

    private static final String PAGE = "query ($first: Int!, $after: String) {"
            + " articles(first: $first, after: $after) {"
            + " totalCount edges { cursor node { id title } }"
            + " pageInfo { hasNextPage hasPreviousPage startCursor endCursor } } }";

    private static Map<String, Object> page(ApplicationContext context, int first, String after) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("first", first);
        variables.put("after", after);
        ExecutionResult result = GraphQL.newGraphQL(context.getBean(GraphQLSchema.class)).build()
                .execute(ExecutionInput.newExecutionInput().query(PAGE).variables(variables).build());
        assertTrue(result.getErrors().isEmpty(), result.getErrors().toString());
        Map<String, Object> data = result.getData();
        @SuppressWarnings("unchecked")
        Map<String, Object> connection = (Map<String, Object>) data.get("articles");
        return connection;
    }

    // tag::paging[]
    @Test
    @SuppressWarnings("unchecked")
    void firstAndAfterPageThroughTheConnection() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(), RelayFixtures.RELAY)) {
            List<String> titles = new ArrayList<>();
            String after = null;
            int pages = 0;
            Map<String, Object> pageInfo;
            do {
                Map<String, Object> connection = page(context, 2, after);
                assertEquals(5, connection.get("totalCount"));
                for (Map<String, Object> edge : (List<Map<String, Object>>) connection.get("edges")) {
                    titles.add((String) ((Map<String, Object>) edge.get("node")).get("title"));
                }
                pageInfo = (Map<String, Object>) connection.get("pageInfo");
                assertEquals(pages > 0, pageInfo.get("hasPreviousPage"));
                after = (String) pageInfo.get("endCursor");
                pages++;
            } while ((Boolean) pageInfo.get("hasNextPage"));
            assertEquals(3, pages);
            assertEquals(List.of("Article 1", "Article 2", "Article 3", "Article 4", "Article 5"), titles);
        }
    }
    // end::paging[]

    @Test
    void aNegativeFirstAndAHugeFirstAreHandled() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(), RelayFixtures.RELAY)) {
            ExecutionResult result = GraphQL.newGraphQL(context.getBean(GraphQLSchema.class)).build()
                    .execute(ExecutionInput.newExecutionInput().query(PAGE).variables(Map.of("first", -1)).build());
            assertFalse(result.getErrors().isEmpty());
            assertEquals(5, ((List<?>) page(context, Integer.MAX_VALUE, null).get("edges")).size());
        }
    }

    @Test
    void anOverflowingCursorIsRejected() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(), RelayFixtures.RELAY)) {
            for (String text : List.of("offset:2147483647", "garbage0")) {
                String cursor = java.util.Base64.getEncoder().encodeToString(text.getBytes());
                ExecutionResult result = GraphQL.newGraphQL(context.getBean(GraphQLSchema.class)).build()
                        .execute(ExecutionInput.newExecutionInput().query(PAGE).variables(Map.of("first", 2, "after", cursor)).build());
                assertFalse(result.getErrors().isEmpty(), text);
            }
        }
    }

    @Test
    void aPageAfterTheLastCursorIsEmpty() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(), RelayFixtures.RELAY)) {
            String last = (String) ((Map<?, ?>) page(context, 5, null).get("pageInfo")).get("endCursor");
            Map<String, Object> connection = page(context, 2, last);
            assertEquals(List.of(), connection.get("edges"));
            Map<?, ?> pageInfo = (Map<?, ?>) connection.get("pageInfo");
            assertFalse((Boolean) pageInfo.get("hasNextPage"));
            assertNull(pageInfo.get("startCursor"));
            assertNull(pageInfo.get("endCursor"));
        }
    }

}
