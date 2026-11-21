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

import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.annotation.GraphQLType;
import jakarta.inject.Singleton;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * The fixtures of the TCK scenario {@code protection}: a chain of nodes that the depth and complexity limits judge, and a
 * field whose stage never completes, so that only the timeout ends the operation.
 */
public final class ProtectionScenario {

    static final String SCENARIO = "protection";

    private ProtectionScenario() {
    }

    @GraphQLType
    public static class ProtectedNode {

        private final String name;
        private final ProtectedNode next;

        public ProtectedNode(String name, ProtectedNode next) {
            this.name = name;
            this.next = next;
        }

        public String getName() {
            return name;
        }

        public ProtectedNode getNext() {
            return next;
        }
    }

    @Singleton
    @GraphQLRootResolver
    @Requires(property = "tck.scenario", value = SCENARIO)
    public static class Queries {

        public ProtectedNode protectedChain() {
            return new ProtectedNode("a", new ProtectedNode("b", new ProtectedNode("c", null)));
        }

        public CompletionStage<String> protectedStalled() {
            return new CompletableFuture<>();
        }
    }
}
