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
package io.micronaut.graphql.tools.mapping.input

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.ArgumentValue
import io.micronaut.graphql.tools.GraphQLToolsConfiguration
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import com.fasterxml.jackson.annotation.JsonIgnore
import io.micronaut.graphql.tools.exceptions.InputFieldNotSettableException
import io.micronaut.graphql.tools.exceptions.MethodNotFoundException
import io.micronaut.graphql.tools.exceptions.UnmappedInputPropertiesException
import org.intellij.lang.annotations.Language

class InputPropertiesSpec extends AbstractTest {

    static final String SPEC_LIST_ARGUMENT = "InputPropertiesSpecListArgument"
    static final String SPEC_NESTED_LIST = "InputPropertiesSpecNestedList"
    static final String SPEC_ARGUMENT_VALUE = "InputPropertiesSpecArgumentValue"
    static final String SPEC_TYPE_RESOLVER = "InputPropertiesSpecTypeResolver"
    static final String SPEC_EXTRA = "InputPropertiesSpecExtra"
    static final String SPEC_EXACT = "InputPropertiesSpecExact"
    static final String SPEC_READ_ONLY = "InputPropertiesSpecReadOnly"
    static final String SPEC_IGNORED = "InputPropertiesSpecIgnored"

    static final String UNMAPPED = GraphQLToolsConfiguration.PREFIX + '.checks.unmapped-input-property'

    @Language("GraphQL")
    static final String ITEM_INPUT = """
input ItemInput {
  sku: String!
  quantity: Int
}
"""

    @Language("GraphQL")
    static final String BASKET_INPUT = """
input BasketInput {
  owner: String
  items: [ItemInput!]
}
"""

    static final String SCHEMA_LIST = """
schema {
  query: Query
}

type Query {
  order(items: [ItemInput!]): String
}
""" + ITEM_INPUT

    static final String SCHEMA_BASKET = """
schema {
  query: Query
}

type Query {
  basket(basket: BasketInput): String
}
""" + BASKET_INPUT + ITEM_INPUT

    static final String SCHEMA_SHOP = """
schema {
  query: Query
}

type Query {
  shop: Shop
}

type Shop {
  name: String
  order(items: [ItemInput!]): String
}
""" + ITEM_INPUT

    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  order(items: [ItemInput!]): String
  basket(basket: BasketInput): String
}
""" + BASKET_INPUT + ITEM_INPUT

    void "an input field without a property fails for an input type used in a List argument"() {
        when:
        startContext(SCHEMA_LIST, SPEC_LIST_ARGUMENT)
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof MethodNotFoundException
        e.cause.message == """[field-not-bound] The property or method `quantity` not found in ${ItemWithoutQuantity.name}'s input.
  GraphQL coordinate: ItemInput.quantity (line 12, column 3)
  Mapped class: ${ItemWithoutQuantity.name}"""
    }

    void "an input field without a property fails for an input type used in a list inside another input type"() {
        when:
        startContext(SCHEMA_BASKET, SPEC_NESTED_LIST)
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof MethodNotFoundException
        e.cause.mappingContext.graphQlInputObjectType == 'ItemInput'
        e.cause.methodName == 'quantity'
    }

    void "an input field without a property fails for an input type wrapped in ArgumentValue"() {
        when:
        startContext(SCHEMA_BASKET, SPEC_ARGUMENT_VALUE)
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof MethodNotFoundException
        e.cause.mappingContext.graphQlInputObjectType == 'ItemInput'
        e.cause.methodName == 'quantity'
    }

    void "an input field without a property fails for an argument of a type resolver method"() {
        when:
        startContext(SCHEMA_SHOP, SPEC_TYPE_RESOLVER)
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof MethodNotFoundException
        e.cause.mappingContext.graphQlInputObjectType == 'ItemInput'
        e.cause.methodName == 'quantity'
    }

    void "default: all settable properties that the input types do not define fail in one error"() {
        given:
        startContext(SCHEMA, SPEC_EXTRA)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof UnmappedInputPropertiesException
        e.cause.message == """[unmapped-input-property] Input classes have properties that their GraphQL input types do not define (set micronaut.graphql.tools.checks.unmapped-input-property to warn or ignore to accept them):
  GraphQL input object type: BasketInput, mapped class: ${ExtraBasket.name}, properties: giftWrap
  GraphQL input object type: ItemInput, mapped class: ${ExtraItem.name}, properties: discount, legacyCode"""
    }

    void "explicit unmapped-properties=fail behaves as the default"() {
        given:
        startContext(SCHEMA, SPEC_EXTRA, [(UNMAPPED): 'fail'])

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof UnmappedInputPropertiesException
        e.cause.unmappedProperties.keySet() == ['BasketInput', 'ItemInput'] as Set
    }

    void "unmapped-properties=ignore: properties that the input type does not define are accepted"() {
        given:
        startContext(SCHEMA, SPEC_EXTRA, [(UNMAPPED): 'ignore'])

        when:
        def result = executeQuery('{ basket(basket: {owner: "jane", items: [{sku: "a", quantity: 2}]}) }')

        then:
        result.errors.isEmpty()
        result.data.basket == 'jane: a x 2 (legacyCode=none, giftWrap=false)'
    }

    void "an input field bound to a read-only property fails the startup"() {
        when:
        startContext(SCHEMA_LIST, SPEC_READ_ONLY)
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof InputFieldNotSettableException
        e.cause.message == """[input-field-not-settable] The input field ItemInput.quantity cannot be set: the property `quantity` of ${ReadOnlyItem.name} has no setter and is not a constructor parameter.
  GraphQL coordinate: ItemInput.quantity (line 12, column 3)
  Mapped class: ${ReadOnlyItem.name}
  Mapped property: quantity"""
    }

    void "a Jackson annotation is not part of the input contract: a JsonIgnore property is set"() {
        given:
        startContext(SCHEMA_LIST, SPEC_IGNORED)

        when:
        def result = executeQuery('{ order(items: [{sku: "a", quantity: 3}]) }')

        then:
        result.errors.isEmpty()
        result.data.order == 'a x 3'
    }

    void "default: input classes that match their input types start"() {
        given:
        startContext(SCHEMA, SPEC_EXACT)

        when:
        def result = executeQuery('{ order(items: [{sku: "a", quantity: 2}]) }')

        then:
        result.errors.isEmpty()
        result.data.order == 'a x 2 (total: 2)'
    }

    @Requires(property = 'spec.name', value = SPEC_LIST_ARGUMENT)
    @GraphQLRootResolver
    static class ListArgumentQuery {
        String order(List<ItemWithoutQuantity> items) {
            return null
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NESTED_LIST)
    @GraphQLRootResolver
    static class NestedListQuery {
        String basket(BasketWithoutQuantity basket) {
            return null
        }
    }

    @Requires(property = 'spec.name', value = SPEC_ARGUMENT_VALUE)
    @GraphQLRootResolver
    static class ArgumentValueQuery {
        String basket(ArgumentValue<BasketWithoutQuantity> basket) {
            return null
        }
    }

    @Requires(property = 'spec.name', value = SPEC_TYPE_RESOLVER)
    @GraphQLRootResolver
    static class TypeResolverQuery {
        Shop shop() {
            return null
        }
    }

    @Requires(property = 'spec.name', value = SPEC_TYPE_RESOLVER)
    @GraphQLTypeResolver(Shop)
    static class ShopResolver {
        String order(Shop shop, List<ItemWithoutQuantity> items) {
            return null
        }
    }

    @Requires(property = 'spec.name', value = SPEC_EXTRA)
    @GraphQLRootResolver
    static class ExtraQuery {
        String basket(ExtraBasket basket) {
            def item = basket.items[0]
            return "${basket.owner}: ${item.sku} x ${item.quantity} (legacyCode=${item.legacyCode}, giftWrap=${basket.giftWrap})"
        }

        String order(List<ExtraItem> items) {
            return null
        }
    }

    @Requires(property = 'spec.name', value = SPEC_EXACT)
    @GraphQLRootResolver
    static class ExactQuery {
        String order(List<ExactItem> items) {
            return items.collect { "${it.sku} x ${it.quantity} (total: ${it.total})" }.join(', ')
        }

        String basket(ExactBasket basket) {
            return null
        }
    }

    @Requires(property = 'spec.name', value = SPEC_READ_ONLY)
    @GraphQLRootResolver
    static class ReadOnlyQuery {
        String order(List<ReadOnlyItem> items) {
            return null
        }
    }

    @GraphQLInput
    static class ReadOnlyItem {
        String sku

        Integer getQuantity() {
            return 1
        }
    }

    @Requires(property = 'spec.name', value = SPEC_IGNORED)
    @GraphQLRootResolver
    static class IgnoredQuery {
        String order(List<IgnoredItem> items) {
            return items.collect { "${it.sku} x ${it.quantity}" }.join(', ')
        }
    }

    @GraphQLInput
    static class IgnoredItem {
        String sku

        @JsonIgnore
        Integer quantity
    }

    @GraphQLType
    static class Shop {
        String name
    }

    @GraphQLInput
    static class ItemWithoutQuantity {
        String sku
    }

    @GraphQLInput
    static class BasketWithoutQuantity {
        String owner
        List<ItemWithoutQuantity> items
    }

    @GraphQLInput
    static class ExactItem {
        String sku
        Integer quantity

        int getTotal() {
            return quantity
        }
    }

    @GraphQLInput
    static class ExactBasket {
        String owner
        List<ExactItem> items
    }

    @GraphQLInput
    static class ExtraItem {
        String sku
        Integer quantity
        String legacyCode = 'none'
        BigDecimal discount
    }

    @GraphQLInput
    static class ExtraBasket {
        String owner
        List<ExtraItem> items
        boolean giftWrap
    }

}
