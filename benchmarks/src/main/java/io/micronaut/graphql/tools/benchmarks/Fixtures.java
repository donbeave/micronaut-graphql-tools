package io.micronaut.graphql.tools.benchmarks;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.graphql.tools.annotation.GraphQLInput;
import io.micronaut.graphql.tools.annotation.GraphQLType;

import java.util.List;

public final class Fixtures {

    private Fixtures() {
    }

    @Introspected
    public enum Color {
        RED, GREEN, BLUE
    }

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

    @GraphQLInput
    public record StockInput(String sku, int quantity, Float weight) {
    }

    @GraphQLInput
    public static class LineInput {

        private String label;
        private PointInput point;
        private List<String> tags;

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public PointInput getPoint() {
            return point;
        }

        public void setPoint(PointInput point) {
            this.point = point;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }
    }

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

    public interface Shape {
    }

    @GraphQLType
    public static class Circle implements Shape {

        public double getRadius() {
            return 1.5;
        }
    }

    @GraphQLType
    public static class Square implements Shape {

        public double getSide() {
            return 2.0;
        }
    }

    public interface Figure {

        String getName();
    }

    @GraphQLType
    public static class Triangle implements Figure {

        @Override
        public String getName() {
            return "triangle";
        }
    }

    @GraphQLType
    public static class Rectangle implements Figure {

        @Override
        public String getName() {
            return "rectangle";
        }
    }

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
