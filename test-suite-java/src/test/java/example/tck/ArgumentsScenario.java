package example.tck;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.ArgumentValue;
import io.micronaut.graphql.tools.annotation.GraphQLInput;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * The fixtures of the TCK scenario {@code arguments}.
 */
public final class ArgumentsScenario {

    private ArgumentsScenario() {
    }

    public enum Color {
        RED, GREEN, BLUE
    }

    @GraphQLInput
    public static class Point {
        private int x;
        private int y;

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
    public static class Shape {
        private String name;
        private Color color;
        private List<Point> points;
        private String note;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Color getColor() {
            return color;
        }

        public void setColor(Color color) {
            this.color = color;
        }

        public List<Point> getPoints() {
            return points;
        }

        public void setPoints(List<Point> points) {
            this.points = points;
        }

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }
    }

    @GraphQLRootResolver
    @Requires(property = "tck.scenario", value = "arguments")
    public static class Queries {

        public String scalars(String text, int number, double ratio, boolean flag, String id) {
            return "text=" + text + " number=" + number + " ratio=" + ratio + " flag=" + flag + " id=" + id;
        }

        public String defaults(@Nullable String text, @Nullable Integer count, @Nullable Color color) {
            return "text=" + text + " count=" + count + " color=" + color;
        }

        public String presence(ArgumentValue<String> text) {
            if (text.isOmitted()) {
                return "omitted";
            }
            return text.isNull() ? "null" : "present:" + text.get();
        }

        public int total(List<Integer> numbers) {
            return numbers.stream().mapToInt(Integer::intValue).sum();
        }

        public int grid(List<List<Integer>> rows) {
            return rows.stream().flatMap(List::stream).mapToInt(Integer::intValue).sum();
        }

        public String shape(Shape shape) {
            String points = shape.getPoints().stream().map(point -> point.getX() + "," + point.getY()).collect(Collectors.joining(";"));
            return "name=" + shape.getName() + " color=" + shape.getColor() + " points=" + points + " note=" + shape.getNote();
        }
    }

    @GraphQLRootResolver
    @Requires(property = "tck.scenario", value = "arguments")
    public static class Mutations {

        private final AtomicInteger total = new AtomicInteger();

        public int add(int amount) {
            return total.addAndGet(amount);
        }
    }

}
