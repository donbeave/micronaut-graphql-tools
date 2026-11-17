package io.micronaut.graphql.tools.benchmarks;

import graphql.schema.DataFetcher;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.schema.idl.SchemaDirectiveWiringEnvironment;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.graphql.tools.SchemaDirective;
import io.micronaut.graphql.tools.SchemaMappingDictionaryCustomizer;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;
import io.micronaut.graphql.tools.benchmarks.Fixtures.Circle;
import io.micronaut.graphql.tools.benchmarks.Fixtures.Color;
import io.micronaut.graphql.tools.benchmarks.Fixtures.Figure;
import io.micronaut.graphql.tools.benchmarks.Fixtures.Item;
import io.micronaut.graphql.tools.benchmarks.Fixtures.PointInput;
import io.micronaut.graphql.tools.benchmarks.Fixtures.Rectangle;
import io.micronaut.graphql.tools.benchmarks.Fixtures.Shape;
import io.micronaut.graphql.tools.benchmarks.Fixtures.Square;
import io.micronaut.graphql.tools.benchmarks.Fixtures.Triangle;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

/**
 * The resolvers and wiring of the benchmarked schema. Every method does the least work that keeps the call from being
 * optimised away, so the benchmarks measure the library's path, not the resolver.
 */
public final class Resolvers {

    /** The most objects a query benchmark lists. */
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

    /** The root resolver. */
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

    /** A field of {@code Item} bound to a method that takes the source. */
    @GraphQLTypeResolver(Item.class)
    public static class ItemResolver {

        public String label(Item item) {
            return "label";
        }

        public String labelWithArgument(Item item, int n) {
            return "label";
        }
    }

    /** The classes of the object types of the union and the interface. */
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

    /** A directive wiring that wraps the fetcher of the field and delegates to it. */
    static final class PassThrough implements SchemaDirectiveWiring {

        @Override
        public GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> environment) {
            DataFetcher<?> original = environment.getFieldDataFetcher();
            return environment.setFieldDataFetcher(original::get);
        }
    }
}
