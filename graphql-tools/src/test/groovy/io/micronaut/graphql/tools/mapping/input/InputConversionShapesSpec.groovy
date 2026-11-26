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

import graphql.language.StringValue
import graphql.schema.Coercing
import graphql.schema.GraphQLScalarType
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractSpec
import io.micronaut.graphql.tools.input.ArgumentValue
import io.micronaut.graphql.tools.mapping.GraphQLScalarMapping
import io.micronaut.graphql.tools.config.GraphQLToolsConfiguration
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.graphql.tools.binding.BindingCode

class InputConversionShapesSpec extends AbstractSpec {

    static final String SPEC_NAME = "InputConversionShapesSpec"
    static final Map DETECT = [:]
    static final String SPEC_DECK = "InputConversionShapesSpecDeck"
    static final String SPEC_NULL_PRIMITIVE = "InputConversionShapesSpecNullPrimitive"
    static final String SPEC_WIDENED = "InputConversionShapesSpecWidened"
    static final String SPEC_BROKEN = "InputConversionShapesSpecBroken"
    static final String SPEC_BROKEN_FIELD = "InputConversionShapesSpecBrokenField"
    static final String SPEC_UNMAPPED_WRAPPER = "InputConversionShapesSpecUnmappedWrapper"

    @Language("GraphQL")
    static final String SCHEMA = """
scalar Code

schema {
  query: Query
}

type Query {
  weigh(item: WeightInput!, extra: Float, exact: Float!): String!
  tags(tags: [String!]!, unique: [String!]): String!
  tree(root: TreeInput!): String!
  mixed(input: MixedInput!): String!
  lookup(code: Code!): String!
}

input WeightInput {
  name: String!
  grams: Float!
  ratio: Float
  count: Int!
}

input TreeInput {
  value: Int!
  children: [TreeInput!]
}

input MixedInput {
  label: String!
  size: Int!
  note: String
  tag: String
}
"""

    @Language("GraphQL")
    static final String SCHEMA_NULL_PRIMITIVE = """
schema {
  query: Query
}

type Query {
  count(input: CountInput): String!
}

input CountInput {
  total: Int
  flag: Boolean
  label: String
}
"""

    @Language("GraphQL")
    static final String SCHEMA_WIDENED = """
schema {
  query: Query
}

type Query {
  widened(input: WidenedInput): String!
}

input WidenedInput {
  count: Int
}
"""

    @Language("GraphQL")
    static final String SCHEMA_BROKEN = """
schema {
  query: Query
}

type Query {
  first(input: BrokenInput): String
  second(input: BrokenInput): String
}

input BrokenInput {
  value: Int
}
"""

    @Language("GraphQL")
    static final String SCHEMA_UNMAPPED_WRAPPER = """
schema {
  query: Query
}

type Query {
  labelled(input: LabelInput): String!
}

input LabelInput {
  label: String
}
"""

    @Language("GraphQL")
    static final String SCHEMA_DECK = """
schema {
  query: Query
}

type Query {
  deck(cards: [Int!]!): String!
}
"""

    void "a Float is narrowed to a float argument and a float input field, and stays a Double for double"() {
        given:
        startContext(SCHEMA, SPEC_NAME, DETECT)

        when:
        def result = executeQuery('{ weigh(item: {name: "a", grams: 2.5, ratio: 0.25, count: 3}, extra: 1.5, exact: 4) }')

        then:
        result.errors.isEmpty()
        result.data.weigh == 'a 2.5 Float 0.25 Double 3 | 1.5 Float | 4.0 Float'
    }

    void "a Float that is omitted or null leaves a nullable property null"() {
        given:
        startContext(SCHEMA, SPEC_NAME, DETECT)

        when:
        def result = executeQuery('{ weigh(item: {name: "a", grams: 1, ratio: null, count: 1}, exact: 2) }')

        then:
        result.errors.isEmpty()
        result.data.weigh == 'a 1.0 Float null null 1 | null null | 2.0 Float'
    }

    void "a list becomes a new mutable List, a Set keeps the order of the list and drops duplicates"() {
        given:
        startContext(SCHEMA, SPEC_NAME, DETECT)

        when:
        def result = executeQuery('{ tags(tags: ["b", "a", "b"], unique: ["b", "a", "b"]) }')

        then:
        result.errors.isEmpty()
        result.data.tags == 'ArrayList [b, a, b, added] LinkedHashSet [b, a]'
    }

    void "an input type that refers to itself is converted at every level"() {
        given:
        startContext(SCHEMA, SPEC_NAME, DETECT)

        when:
        def result = executeQuery('{ tree(root: {value: 1, children: [{value: 2}, {value: 3, children: [{value: 4}]}]}) }')

        then:
        result.errors.isEmpty()
        result.data.tree == '1(2,3(4))'
    }

    void "an input class is built through its constructor, and its other fields through their setters"() {
        given:
        startContext(SCHEMA, SPEC_NAME, DETECT)

        when:
        def omitted = executeQuery('{ mixed(input: {label: "l", size: 2}) }')
        def given = executeQuery('{ mixed(input: {label: "l", size: 2, note: "n", tag: "t"}) }')
        def explicitNull = executeQuery('{ mixed(input: {label: "l", size: 2, note: null, tag: null}) }')

        then:
        omitted.errors.isEmpty()
        omitted.data.mixed == 'l 2 note=null tag=omitted'
        given.data.mixed == 'l 2 note=n tag=value(t)'
        explicitNull.data.mixed == 'l 2 note=null tag=null'
    }

    void "a scalar value that is not an instance of the declared class goes through the Micronaut conversion service"() {
        given:
        startContext(SCHEMA, SPEC_NAME, DETECT)
        def uuid = UUID.randomUUID()

        when:
        def result = executeQuery("{ lookup(code: \"${uuid}\") }")

        then:
        result.errors.isEmpty()
        result.data.lookup == "${uuid} (UUID)"
    }

    void "an explicit null or an omitted value for a primitive property leaves its default"() {
        given:
        startContext(SCHEMA_NULL_PRIMITIVE, SPEC_NULL_PRIMITIVE, [(GraphQLToolsConfiguration.PREFIX + '.strict'): false])

        when:
        def nulls = executeQuery('{ count(input: {total: null, flag: null, label: null}) }')
        def omitted = executeQuery('{ count(input: {}) }')

        then:
        nulls.errors.isEmpty()
        nulls.data.count == 'total=7 flag=false label=null'
        omitted.data.count == 'total=7 flag=false label=default'
    }

    void "a null for a primitive constructor parameter keeps the default, whatever the type of the property"() {
        given:
        startContext(SCHEMA_WIDENED, SPEC_WIDENED, [(GraphQLToolsConfiguration.PREFIX + '.strict'): false])

        when:
        def result = executeQuery('{ widened(input: {count: null}) }')

        then:
        result.errors.isEmpty()
        result.data.widened == 'count=0'
    }

    void "an input type that fails to bind fails the startup with its own error at every use"() {
        when:
        startContext(SCHEMA_BROKEN, SPEC_BROKEN)
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.getCode() == BindingCode.BINDING_FAILURES
        e.cause.message.contains('2 errors')
        e.cause.message.count('[class-not-introspected]') == 2
    }

    void "an input type that fails to bind after its converter was registered fails the startup at every use"() {
        when:
        startContext(SCHEMA_BROKEN, SPEC_BROKEN_FIELD)
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.getCode() == BindingCode.BINDING_FAILURES
        e.cause.message.contains('2 errors')
    }

    void "an ArgumentValue property that no input field defines is omitted"() {
        given:
        startContext(SCHEMA_UNMAPPED_WRAPPER, SPEC_UNMAPPED_WRAPPER, [(GraphQLToolsConfiguration.PREFIX + '.strict'): false])

        when:
        def result = executeQuery('{ labelled(input: {label: "l"}) }')

        then:
        result.errors.isEmpty()
        result.data.labelled == 'l omitted'
    }

    void "a list declared as a collection that no list converts to fails the startup"() {
        when:
        startContext(SCHEMA_DECK, SPEC_DECK)
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.getCode() == BindingCode.UNSUPPORTED_INPUT_TYPE
        e.cause.reason.contains(ArrayDeque.name)
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Factory
    static class Scalars {
        @Bean
        @Singleton
        GraphQLScalarMapping code() {
            // parses to a String, although the mapping declares UUID: the conversion service closes the gap
            return GraphQLScalarMapping.builder(GraphQLScalarType.newScalar().name('Code').coercing(new Coercing<Object, String>() {
                @Override
                String serialize(Object value) { value.toString() }

                @Override
                Object parseValue(Object value) { throw new UnsupportedOperationException() }

                @Override
                Object parseLiteral(Object value) { ((StringValue) value).value }
            }).build()).outputClasses(UUID).inputClasses(UUID).build()
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @GraphQLRootResolver
    static class Query {
        String weigh(WeightInput item, Float extra, float exact) {
            return "${item.name} ${item.grams} ${item.grams.class.simpleName} ${item.ratio} ${item.ratio?.class?.simpleName} " +
                    "${item.count} | ${extra} ${extra?.class?.simpleName} | ${exact} ${((Object) exact).class.simpleName}"
        }

        String tags(List<String> tags, Set<String> unique) {
            tags.add('added')
            return "${tags.class.simpleName} ${tags} ${unique.class.simpleName} ${unique}"
        }

        String tree(TreeInput root) {
            return root.show()
        }

        String mixed(MixedInput input) {
            return "${input.label} ${input.size} note=${input.note} tag=${show(input.tag)}"
        }

        String lookup(UUID code) {
            return "${code} (${code.class.simpleName})"
        }

        private static String show(ArgumentValue<String> value) {
            return value.omitted ? 'omitted' : value.null ? 'null' : "value(${value.value})"
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NULL_PRIMITIVE)
    @Singleton
    @GraphQLRootResolver
    static class NullPrimitiveQuery {
        String count(CountInput input) {
            return "total=${input.total} flag=${input.flag} label=${input.label}"
        }
    }

    @Requires(property = 'spec.name', value = SPEC_WIDENED)
    @Singleton
    @GraphQLRootResolver
    static class WidenedQuery {
        String widened(WidenedInput input) {
            return "count=${input.count}"
        }
    }

    @Requires(property = 'spec.name', value = SPEC_BROKEN)
    @Singleton
    @GraphQLRootResolver
    static class BrokenQuery {
        String first(BrokenInput input) {
            return null
        }

        String second(BrokenInput input) {
            return null
        }
    }

    @Requires(property = 'spec.name', value = SPEC_BROKEN_FIELD)
    @Singleton
    @GraphQLRootResolver
    static class BrokenFieldQuery {
        String first(MissingPropertyInput input) {
            return null
        }

        String second(MissingPropertyInput input) {
            return null
        }
    }

    @Requires(property = 'spec.name', value = SPEC_UNMAPPED_WRAPPER)
    @Singleton
    @GraphQLRootResolver
    static class UnmappedWrapperQuery {
        String labelled(UnmappedWrapperInput input) {
            return "${input.label} ${input.note.omitted ? 'omitted' : input.note.getRequired()}"
        }
    }

    @Requires(property = 'spec.name', value = SPEC_DECK)
    @Singleton
    @GraphQLRootResolver
    static class DeckQuery {
        String deck(ArrayDeque<Integer> cards) {
            return null
        }
    }

    @GraphQLInput
    static class WeightInput {
        String name
        float grams
        Double ratio
        int count
    }

    @GraphQLInput
    static class TreeInput {
        int value
        List<TreeInput> children

        String show() {
            return children ? "${value}(${children.collect { it.show() }.join(',')})" : "${value}"
        }
    }

    @GraphQLInput
    static class MixedInput {
        final String label
        final int size
        final ArgumentValue<String> tag
        String note

        MixedInput(String label, int size, ArgumentValue<String> tag) {
            this.label = label
            this.size = size
            this.tag = tag
        }
    }

    @GraphQLInput
    static class WidenedInput {
        private final int count

        WidenedInput(int count) {
            this.count = count
        }

        Integer getCount() {
            return count
        }
    }

    static class BrokenInput {
        Integer value
    }

    @GraphQLInput
    static class MissingPropertyInput {
        Integer other
    }

    @GraphQLInput
    static class UnmappedWrapperInput {
        String label
        ArgumentValue<String> note = ArgumentValue.ofNullable('kept')
    }

    @GraphQLInput
    static class CountInput {
        int total = 7
        boolean flag
        String label = 'default'
    }

}
