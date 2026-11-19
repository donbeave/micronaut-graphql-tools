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
package example.app;

import graphql.ExecutionResult;
import graphql.GraphQL;
import io.micronaut.context.ApplicationContext;

public final class Application {

    private Application() {
    }

    /**
     * Starts the application, which binds the schema to the resolvers of every module, and runs one query through all of them.
     * A binding failure ends the process with the exception on the standard error stream.
     */
    public static void main(String[] args) {
        try (ApplicationContext context = ApplicationContext.run()) {
            ExecutionResult result = context.getBean(GraphQL.class).execute(
                    "{ book(id: \"7\") { id title author { name } } books { id } authors(genre: FICTION) { name genre } }");
            System.out.println("errors: " + result.getErrors());
            System.out.println("data: " + result.getData());
        } catch (RuntimeException e) {
            e.printStackTrace();
            // a context that failed to start leaves its executor threads running
            System.exit(1);
        }
    }
}
