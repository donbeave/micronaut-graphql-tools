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

import graphql.schema.DataFetcher;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.schema.idl.SchemaDirectiveWiringEnvironment;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.directive.SchemaDirective;
import io.micronaut.graphql.tools.customizer.SchemaMappingDictionaryCustomizer;
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
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

public final class Resolvers {

    public static final int MAX_ITEMS = 10_000;

    private static final List<Item> ITEMS = items();

    private Resolvers() {
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
    }

    static final class PassThrough implements SchemaDirectiveWiring {

        @Override
        public GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> environment) {
            DataFetcher<?> original = environment.getFieldDataFetcher();
            return environment.setFieldDataFetcher(original::get);
        }
    }
}
