package example.tck

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.micronaut.graphql.tools.ArgumentValue
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

import java.util.concurrent.atomic.AtomicInteger

// The fixtures of the TCK scenario `arguments`.

enum Color {
    RED, GREEN, BLUE
}

@CompileStatic
@GraphQLInput
class Point {
    int x
    int y
}

@CompileStatic
@GraphQLInput
class Shape {
    String name
    Color color
    List<Point> points
    String note
}

@CompileStatic
@GraphQLRootResolver
@Requires(property = 'tck.scenario', value = 'arguments')
class ArgumentsQueries {

    String scalars(String text, int number, double ratio, boolean flag, String id) {
        return "text=$text number=$number ratio=$ratio flag=$flag id=$id".toString()
    }

    String defaults(@Nullable String text, @Nullable Integer count, @Nullable Color color) {
        return "text=$text count=$count color=$color".toString()
    }

    String presence(ArgumentValue<String> text) {
        if (text.omitted) {
            return 'omitted'
        }
        return text.isNull() ? 'null' : 'present:' + text.get()
    }

    int total(List<Integer> numbers) {
        return numbers.sum(0) as int
    }

    int grid(List<List<Integer>> rows) {
        return rows.collectMany { it }.sum(0) as int
    }

    String shape(Shape shape) {
        String points = shape.points.collect { "${it.x},${it.y}" }.join(';')
        return "name=$shape.name color=$shape.color points=$points note=$shape.note".toString()
    }
}

@CompileStatic
@GraphQLRootResolver
@Requires(property = 'tck.scenario', value = 'arguments')
class ArgumentsMutations {

    private final AtomicInteger total = new AtomicInteger()

    int add(int amount) {
        return total.addAndGet(amount)
    }
}

// tag::optional-helpers[]
@CompileStatic
class OptionalHelpers {

    static String describePresence(ArgumentValue<String> text) {
        return text.asOptional().map { 'present:' + it }.orElse('absent')
    }

    static void rememberIfPresent(ArgumentValue<String> text, List<String> seen) {
        text.ifPresent { seen << it }
    }
}
// end::optional-helpers[]
