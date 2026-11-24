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

import graphql.schema.DataFetcher;
import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.annotation.GraphQLType;
import io.micronaut.graphql.tools.execution.FieldDescriptor;
import io.micronaut.graphql.tools.execution.GraphQLFieldFetcherDecorator;
import jakarta.inject.Singleton;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * The fixtures of the TCK scenario {@code field-decorator}: a decorator that only sees fields with arguments, and
 * resolvers for fields with and without arguments and for a property.
 */
public final class FieldDecoratorScenario {

    private FieldDecoratorScenario() {
    }

    @Requires(property = "tck.scenario", value = "field-decorator")
    // tag::decorator[]
    @Singleton
    public static class MarkingDecorator implements GraphQLFieldFetcherDecorator {

        @Override
        public DataFetcher<Object> decorate(FieldDescriptor field, DataFetcher<Object> fetcher) {
            return environment -> {
                Object value = fetcher.get(environment);
                return value instanceof CompletionStage<?> stage ? stage.thenApply(it -> "decorated:" + it) : "decorated:" + value;
            };
        }
    }
    // end::decorator[]

    @GraphQLType
    public static class DecoratedBox {

        public String getLabel() {
            return "label";
        }
    }

    @Singleton
    @GraphQLRootResolver
    @Requires(property = "tck.scenario", value = "field-decorator")
    public static class DecoratorQueries {

        public String decoratedEcho(String text) {
            return text;
        }

        public CompletionStage<String> decoratedAsync(String text) {
            return CompletableFuture.completedFuture(text);
        }

        public String undecoratedPlain() {
            return "plain";
        }

        public DecoratedBox decoratedBox() {
            return new DecoratedBox();
        }
    }
}
