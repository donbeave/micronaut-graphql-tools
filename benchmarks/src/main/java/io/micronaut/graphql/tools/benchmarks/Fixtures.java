package io.micronaut.graphql.tools.benchmarks;

import io.micronaut.graphql.tools.annotation.GraphQLInput;
import io.micronaut.graphql.tools.annotation.GraphQLType;

/**
 * The Java types the benchmarked schema is bound to.
 */
public final class Fixtures {

    private Fixtures() {
    }

    /** An enum argument. */
    public enum Color {
        RED, GREEN, BLUE
    }

    /** An input object argument. */
    @GraphQLInput
    public static class PointInput {

        private String label;
        private int x;
        private int y;

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public int getX() {
            return x;
        }

        public void setX(int x) {
            this.x = x;
        }

        public int getY() {
            return y;
        }

        public void setY(int y) {
            this.y = y;
        }
    }

    /** The object the query benchmarks list; its fields are read by property fetchers. */
    @GraphQLType
    public static class Item {

        private final String id;
        private final String name;
        private final int price;

        public Item(String id, String name, int price) {
            this.id = id;
            this.name = name;
            this.price = price;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public int getPrice() {
            return price;
        }
    }

    /** A union with two members, resolved by the exact class. */
    public interface Shape {
    }

    /** A member of {@link Shape}. */
    @GraphQLType
    public static class Circle implements Shape {

        public double getRadius() {
            return 1.5;
        }
    }

    /** A member of {@link Shape}. */
    @GraphQLType
    public static class Square implements Shape {

        public double getSide() {
            return 2.0;
        }
    }

    /** An interface with two implementations, resolved by the exact class. */
    public interface Figure {

        String getName();
    }

    /** An implementation of {@link Figure}. */
    @GraphQLType
    public static class Triangle implements Figure {

        @Override
        public String getName() {
            return "triangle";
        }
    }

    /** An implementation of {@link Figure}. */
    @GraphQLType
    public static class Rectangle implements Figure {

        @Override
        public String getName() {
            return "rectangle";
        }
    }

    /** A plain class read by graphql-java's own {@code PropertyDataFetcher}, for comparison. */
    public static class PlainItem {

        private final String name;

        public PlainItem(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
