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
package io.micronaut.graphql.tools.benchmarks;

import graphql.GraphQLContext;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.schema.idl.SchemaDirectiveWiringEnvironment;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.directive.SchemaDirective;
import io.micronaut.graphql.tools.execution.FieldDescriptor;
import io.micronaut.graphql.tools.execution.FieldFetcherDecorator;
import io.micronaut.graphql.tools.customizer.SchemaMappingDictionaryCustomizer;
import io.micronaut.graphql.tools.annotation.GraphQLContextValue;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;
import io.micronaut.graphql.tools.benchmarks.Fixtures.Circle;
import io.micronaut.graphql.tools.benchmarks.Fixtures.Color;
import io.micronaut.graphql.tools.benchmarks.Fixtures.Figure;
import io.micronaut.graphql.tools.benchmarks.Fixtures.Item;
import io.micronaut.graphql.tools.benchmarks.Fixtures.LineInput;
import io.micronaut.graphql.tools.benchmarks.Fixtures.PointInput;
import io.micronaut.graphql.tools.benchmarks.Fixtures.Rectangle;
import io.micronaut.graphql.tools.benchmarks.Fixtures.Shape;
import io.micronaut.graphql.tools.benchmarks.Fixtures.Square;
import io.micronaut.graphql.tools.benchmarks.Fixtures.StockInput;
import io.micronaut.graphql.tools.benchmarks.Fixtures.Triangle;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import jakarta.inject.Singleton;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

public final class Resolvers {

    public static final int MAX_ITEMS = 10_000;

    private static final List<Item> ITEMS = items();

    /** How many fields the benchmark decorators were offered at startup; the benchmark that selects them resets and reads it. */
    static final AtomicInteger OFFERED = new AtomicInteger();

    /** How many fields the wrapping benchmark decorator wrapped at startup. */
    static final AtomicInteger WRAPPED = new AtomicInteger();

    /** A future that is already done: what an async field returns when its value is ready. */
    static final CompletableFuture<String> DONE = CompletableFuture.completedFuture("pong");

    /** The context of an execution that the library's invocation started, which an async fetcher needs to find its state. */
    private static volatile GraphQLContext captured;

    private Resolvers() {
    }

    static GraphQLContext capturedContext() {
        return captured;
    }

    private static List<Item> items() {
        List<Item> items = new ArrayList<>(MAX_ITEMS);
        for (int i = 0; i < MAX_ITEMS; i++) {
            items.add(new Item("id-" + i, "name-" + i, i));
        }
        return List.copyOf(items);
    }

    @Requires(property = "benchmarks.large", notEquals = "true")
    @Singleton
    @GraphQLRootResolver
    public static class Query {

        public String ping() {
            return "pong";
        }

        public String scalar(int n) {
            return "x";
        }

        public String injected(GraphQLContext context, Locale locale, @GraphQLContextValue("tenant") String tenant,
                               DataFetchingEnvironment environment) {
            return "x";
        }

        public String injectedState(HttpRequest<?> request, Optional<MutableHttpResponse<?>> response,
                                    Principal principal) {
            return "x";
        }

        public String enumArg(Color color) {
            return "x";
        }

        public String input(PointInput input) {
            return input.getLabel();
        }

        public String inputs(List<PointInput> inputs) {
            return "x";
        }

        public String tags(List<String> tags) {
            return "x";
        }

        public String stock(StockInput stock) {
            return stock.sku();
        }

        public String line(LineInput line) {
            return line.getLabel();
        }

        public List<Item> items(int count) {
            return ITEMS.subList(0, count);
        }

        public Shape shape(boolean circle) {
            return circle ? new Circle() : new Square();
        }

        public Figure figure(boolean triangle) {
            return triangle ? new Triangle() : new Rectangle();
        }

        public String guarded() {
            return "pong";
        }

        public CompletionStage<String> asyncDone() {
            return DONE;
        }

        public CompletionStage<String> asyncPending() {
            return new CompletableFuture<>();
        }

        public String captured(DataFetchingEnvironment environment) {
            captured = environment.getGraphQlContext();
            return "pong";
        }
    }

    @Requires(property = "benchmarks.large", notEquals = "true")
    @Singleton
    @GraphQLTypeResolver(Item.class)
    public static class ItemResolver {

        public String label(Item item) {
            return "label";
        }

        public String labelWithArgument(Item item, int n) {
            return "label";
        }
    }

    @Requires(property = "benchmarks.large", notEquals = "true")
    @Factory
    public static class Wiring {

        @Bean
        @Singleton
        SchemaMappingDictionaryCustomizer dictionary() {
            return dictionary -> dictionary
                    .registerType("Circle", Circle.class)
                    .registerType("Square", Square.class)
                    .registerType("Triangle", Triangle.class)
                    .registerType("Rectangle", Rectangle.class);
        }

        @Bean
        @Singleton
        SchemaDirective guard() {
            return SchemaDirective.named("guard", new PassThrough());
        }

        /** A decorator that is offered every field and selects none: the fetchers must stay as they are. */
        @Bean
        @Singleton
        @Requires(property = "benchmarks.decorator", value = "unselective")
        FieldFetcherDecorator unselective() {
            return new FieldFetcherDecorator() {
                @Override
                public boolean decoratesFieldsWithoutArguments() {
                    return true;
                }

                @Override
                public boolean decoratesTrivialFields() {
                    return true;
                }

                @Override
                public DataFetcher<Object> decorate(FieldDescriptor field, DataFetcher<Object> fetcher) {
                    OFFERED.incrementAndGet();
                    return fetcher;
                }
            };
        }

        /** A decorator that wraps every field it is offered in a fetcher that only delegates. */
        @Bean
        @Singleton
        @Requires(property = "benchmarks.decorator", value = "wrapAll")
        FieldFetcherDecorator wrapAll() {
            return new FieldFetcherDecorator() {
                @Override
                public boolean decoratesFieldsWithoutArguments() {
                    return true;
                }

                @Override
                public boolean decoratesTrivialFields() {
                    return true;
                }

                @Override
                public DataFetcher<Object> decorate(FieldDescriptor field, DataFetcher<Object> fetcher) {
                    OFFERED.incrementAndGet();
                    WRAPPED.incrementAndGet();
                    return fetcher::get;
                }
            };
        }
    }

    static final class PassThrough implements SchemaDirectiveWiring {

        @Override
        public GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> environment) {
            DataFetcher<?> original = environment.getFieldDataFetcher();
            return environment.setFieldDataFetcher(original::get);
        }
    }
}
