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

import jakarta.inject.Singleton;
import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.annotation.GraphQLType;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * The fixtures of the TCK scenario {@code errors}.
 */
public final class ErrorsScenario {

    private ErrorsScenario() {
    }

    @GraphQLType
    public static class Parent {
        public String getName() {
            return "parent";
        }

        public String getBroken() {
            throw new IllegalStateException("boom");
        }
    }

    @Singleton
    @GraphQLRootResolver
    @Requires(property = "tck.scenario", value = "errors")
    public static class Queries {

        public String ok() {
            return "fine";
        }

        public String failNullable() {
            throw new IllegalStateException("boom");
        }

        public String failNonNull() {
            throw new IllegalStateException("boom");
        }

        public CompletionStage<String> failFuture() {
            return CompletableFuture.failedFuture(new IllegalStateException("async boom"));
        }

        public Parent nested() {
            return new Parent();
        }

        public Parent nestedNonNull() {
            return new Parent();
        }

        public int echo(int value) {
            return value;
        }
    }

}
