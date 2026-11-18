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
package example.tck

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.annotation.Nullable
import io.micronaut.graphql.tools.ArgumentValue
import io.micronaut.graphql.tools.annotation.GraphQLArgument
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

import java.util.concurrent.atomic.AtomicInteger

// The fixtures of the TCK scenario `arguments`.

@Introspected
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

// tag::constructor-input[]
@CompileStatic
@GraphQLInput
class Stock {
    final String sku
    final int quantity
    final Float weight
    final List<String> tags
    final Set<Color> colors
    final ArgumentValue<String> comment

    Stock(String sku, int quantity, Float weight, List<String> tags, Set<Color> colors, ArgumentValue<String> comment) {
        this.sku = sku
        this.quantity = quantity
        this.weight = weight
        this.tags = tags
        this.colors = colors
        this.comment = comment
    }
}
// end::constructor-input[]

// tag::nested-constructor-input[]
@CompileStatic
@GraphQLInput
class Dimensions {
    final int width
    final ArgumentValue<String> unit

    Dimensions(int width, ArgumentValue<String> unit) {
        this.width = width
        this.unit = unit
    }
}

@CompileStatic
@GraphQLInput
class Part {
    final String name
    final ArgumentValue<String> note

    Part(String name, ArgumentValue<String> note) {
        this.name = name
        this.note = note
    }
}

@CompileStatic
@GraphQLInput
class Crate {
    final String label
    final Dimensions dimensions
    final List<Part> parts
    final ArgumentValue<String> memo

    Crate(String label, Dimensions dimensions, List<Part> parts, ArgumentValue<String> memo) {
        this.label = label
        this.dimensions = dimensions
        this.parts = parts
        this.memo = memo
    }
}
// end::nested-constructor-input[]

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

    String stock(Stock stock) {
        return "sku=$stock.sku quantity=$stock.quantity weight=$stock.weight tags=$stock.tags colors=$stock.colors comment=${presence(stock.comment)}".toString()
    }

    String crate(Crate crate) {
        String parts = crate.parts.collect { "${it.name}:${presence(it.note)}" }.join(', ')
        return "label=$crate.label dimensions=$crate.dimensions.width unit=${presence(crate.dimensions.unit)} parts=[$parts] memo=${presence(crate.memo)}".toString()
    }

    // tag::by-name[]
    String window(@GraphQLArgument('in') String scope, int offset, int first) {
        return "first=$first offset=$offset in=$scope".toString()
    }
    // end::by-name[]

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
