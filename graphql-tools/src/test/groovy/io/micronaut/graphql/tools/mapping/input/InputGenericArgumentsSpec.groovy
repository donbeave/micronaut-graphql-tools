package io.micronaut.graphql.tools.mapping.input

import graphql.ExecutionInput
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import org.intellij.lang.annotations.Language

/**
 * Arguments whose Java type is generic ({@code List<Input>}, {@code List<Enum>}, nested lists, {@code Set<Input>}) are
 * converted with their full generic type, so the resolver receives instances of the declared element classes. Input
 * objects with generic fields keep working.
 */
class InputGenericArgumentsSpec extends AbstractTest {

    static final String SPEC_NAME = "InputGenericArgumentsSpec"
    static final String SPEC_JAVA = "InputGenericArgumentsSpecJava"

    @Language("GraphQL")
    static final String SCHEMA_JAVA = """
schema {
  query: Query
}

type Query {
  countries(selections: [PriceSelectionInput!]!): String!
  regions(selections: [PriceSelectionInput!]!): String!
  markets(selections: [PriceSelectionInput!]!): String!
  supers(selections: [PriceSelectionInput!]!): String!
}

input PriceSelectionInput {
  country: String!
}
"""

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  countries(selections: [PriceSelectionInput!]!): String!
  colors(colors: [Color!]): String!
  matrix(rows: [[PriceSelectionInput!]!]!): String!
  uniqueCountries(selections: [PriceSelectionInput!]!): String!
  grid(input: GridInput!): String!
  shop: Shop
}

type Shop {
  name: String
  prices(selections: [PriceSelectionInput!]!): String!
}

enum Color {
  RED
  GREEN
}

enum BlockType {
  BESTSELLERS
  BANNERS
}

input PriceSelectionInput {
  country: String!
  currency: String
}

input GridInput {
  blockTypes: [BlockType!]!
  limit: Int
  priceSelection: PriceSelectionInput
}
"""

    void "a top-level List of input objects is converted to the declared element class"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = executeQuery("""
{
    countries(selections: [{country: "US", currency: "USD"}, {country: "GB"}])
}
""")

        then:
        result.errors.isEmpty()
        result.data.countries == 'PriceSelectionInput:US/USD PriceSelectionInput:GB'
    }

    void "a top-level List of input objects from a variable"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = graphQLBean.execute(ExecutionInput.newExecutionInput()
                .query('query ($s: [PriceSelectionInput!]!) { countries(selections: $s) }')
                .variables([s: [[country: 'DE', currency: 'EUR']]])
                .build())

        then:
        result.errors.isEmpty()
        result.data.countries == 'PriceSelectionInput:DE/EUR'
    }

    void "a top-level List of enums is converted to the enum class"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = executeQuery("""
{
    colors(colors: [GREEN, RED])
}
""")

        then:
        result.errors.isEmpty()
        result.data.colors == 'Color.GREEN Color.RED'
    }

    void "a nested List of input objects is converted to the declared element class"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = executeQuery("""
{
    matrix(rows: [[{country: "US"}], [{country: "GB"}, {country: "FR"}]])
}
""")

        then:
        result.errors.isEmpty()
        result.data.matrix == '[PriceSelectionInput:US] [PriceSelectionInput:GB, PriceSelectionInput:FR]'
    }

    void "a Set of input objects is converted to a Set of the declared element class"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = executeQuery("""
{
    uniqueCountries(selections: [{country: "US"}, {country: "US"}])
}
""")

        then:
        result.errors.isEmpty()
        result.data.uniqueCountries == 'Set of 1 PriceSelectionInput'
    }

    void "a List of input objects as an argument of a type resolver method"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = executeQuery("""
{
    shop {
        prices(selections: [{country: "US"}])
    }
}
""")

        then:
        result.errors.isEmpty()
        result.data.shop.prices == 'Main: PriceSelectionInput:US'
    }

    void "a top-level List of input objects reaches a Java resolver as the declared element class"() {
        given:
        startContext(SCHEMA_JAVA, SPEC_JAVA)

        when:
        def result = executeQuery('{ countries(selections: [{country: "US"}, {country: "GB"}]) }')

        then:
        result.errors.isEmpty()
        result.data.countries == 'US GB'
    }

    void "a wildcard List<? extends Input> argument is converted to its bound"() {
        given:
        startContext(SCHEMA_JAVA, SPEC_JAVA)

        when:
        def result = executeQuery('{ regions(selections: [{country: "US"}]) }')

        then:
        result.errors.isEmpty()
        result.data.regions == "${JavaPriceSelection.simpleName}:US"
    }

    void "a wildcard List<? super Input> argument is converted to its bound"() {
        given:
        startContext(SCHEMA_JAVA, SPEC_JAVA)

        when:
        def result = executeQuery('{ supers(selections: [{country: "US"}]) }')

        then:
        result.errors.isEmpty()
        result.data.supers == "${JavaPriceSelection.simpleName}:US"
    }

    void "a Collection<Input> argument is converted to a collection of the declared element class"() {
        given:
        startContext(SCHEMA_JAVA, SPEC_JAVA)

        when:
        def result = executeQuery('{ markets(selections: [{country: "US"}, {country: "FR"}]) }')

        then:
        result.errors.isEmpty()
        result.data.markets == 'ArrayList:US FR'
    }

    void "an input object with a list of enums and a nested input object"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = executeQuery("""
{
    grid(input: {blockTypes: [BESTSELLERS, BANNERS], limit: 5, priceSelection: {country: "US"}})
}
""")

        then:
        result.errors.isEmpty()
        result.data.grid == 'GridInput blockTypes=[BESTSELLERS, BANNERS] (BlockType) limit=5 priceSelection=PriceSelectionInput:US'
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {

        String countries(List<PriceSelectionInput> selections) {
            return selections.collect { describe(it) }.join(' ')
        }

        String colors(List<Color> colors) {
            return colors.collect { Color it -> "${it.class.simpleName}.${it.name()}" }.join(' ')
        }

        String matrix(List<List<PriceSelectionInput>> rows) {
            return rows.collect { List<PriceSelectionInput> row -> '[' + row.collect { describe(it) }.join(', ') + ']' }
                    .join(' ')
        }

        String uniqueCountries(Set<PriceSelectionInput> selections) {
            return "Set of ${selections.size()} ${selections.first().class.simpleName}"
        }

        String grid(GridInput input) {
            return "GridInput blockTypes=${input.blockTypes} (${input.blockTypes[0].class.simpleName}) " +
                    "limit=${input.limit} priceSelection=${describe(input.priceSelection)}"
        }

        Shop shop() {
            return new Shop(name: 'Main')
        }

        static String describe(PriceSelectionInput selection) {
            String currency = selection.currency != null ? "/${selection.currency}" : ''
            return "${selection.class.simpleName}:${selection.country}${currency}"
        }

    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLTypeResolver(Shop)
    static class ShopResolver {

        String prices(Shop shop, List<PriceSelectionInput> selections) {
            return "${shop.name}: " + selections.collect { Query.describe(it) }.join(' ')
        }

    }

    @GraphQLType
    static class Shop {
        String name
    }

    static enum Color {
        RED,
        GREEN
    }

    static enum BlockType {
        BESTSELLERS,
        BANNERS
    }

    @GraphQLInput
    static class PriceSelectionInput {
        String country
        String currency

        @Override
        boolean equals(Object other) {
            return other instanceof PriceSelectionInput && other.country == country && other.currency == currency
        }

        @Override
        int hashCode() {
            return Objects.hash(country, currency)
        }
    }

    @GraphQLInput
    static class GridInput {
        List<BlockType> blockTypes
        Integer limit
        PriceSelectionInput priceSelection
    }

}
