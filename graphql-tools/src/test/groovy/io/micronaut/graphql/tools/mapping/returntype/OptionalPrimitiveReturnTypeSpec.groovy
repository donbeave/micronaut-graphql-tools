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
package io.micronaut.graphql.tools.mapping.returntype

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.exceptions.UnsupportedReturnTypeException
import org.intellij.lang.annotations.Language

class OptionalPrimitiveReturnTypeSpec extends AbstractTest {

    static final String SPEC_NAME = "OptionalPrimitiveReturnTypeSpec"
    static final String SPEC_NAME_NON_NULL = "OptionalPrimitiveReturnTypeSpecNonNull"
    static final String SPEC_NAME_NON_NULL_ELEMENT = "OptionalPrimitiveReturnTypeSpecNonNullElement"
    static final String SPEC_NAME_WRONG_CLASS = "OptionalPrimitiveReturnTypeSpecWrongClass"

    void "OptionalInt, OptionalLong and OptionalDouble on nullable fields and list elements return the value or null"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

scalar Long

type Query {
  count: Int
  absentCount: Int
  big: Long
  absentBig: Long
  ratio: Float
  absentRatio: Float
  counts: [Int]
}
"""
        startContext(schema, SPEC_NAME)

        when:
        def result = executeQuery("""
{
    count
    absentCount
    big
    absentBig
    ratio
    absentRatio
    counts
}
""")

        then:
        result.errors.isEmpty()
        result.data == [count: 3, absentCount: null, big: 9000000000, absentBig: null, ratio: 1.5d,
                        absentRatio: null, counts: [1, null, 3]]
    }

    void "OptionalInt on a non-null field fails at startup with the coordinate"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  count: Int!
}
"""
        startContext(schema, SPEC_NAME_NON_NULL)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof UnsupportedReturnTypeException
        e.cause.message.startsWith("[unsupported-return-type] The return type of the method or property is not supported for this field: " +
                "java.util.OptionalInt is only allowed for a nullable GraphQL type, the field type is Int!.")
        e.cause.message.contains("GraphQL coordinate: Query.count (line 7, column 3)")
    }

    void "OptionalInt for a non-null list element fails at startup"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  counts: [Int!]
}
"""
        startContext(schema, SPEC_NAME_NON_NULL_ELEMENT)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof UnsupportedReturnTypeException
        e.cause.message.startsWith("[unsupported-return-type] The return type of the method or property is not supported for this field: " +
                "java.util.OptionalInt is only allowed for a nullable GraphQL type, the list element type is Int!.")
        e.cause.message.contains("GraphQL coordinate: Query.counts")
    }

    void "OptionalLong on an Int field fails at startup, as a plain long would"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  count: Int
}
"""
        startContext(schema, SPEC_NAME_WRONG_CLASS)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause.message.contains("GraphQL coordinate: Query.count")
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        OptionalInt count() {
            return OptionalInt.of(3)
        }

        OptionalInt absentCount() {
            return OptionalInt.empty()
        }

        OptionalLong big() {
            return OptionalLong.of(9000000000L)
        }

        OptionalLong absentBig() {
            return OptionalLong.empty()
        }

        OptionalDouble ratio() {
            return OptionalDouble.of(1.5d)
        }

        OptionalDouble absentRatio() {
            return OptionalDouble.empty()
        }

        List<OptionalInt> counts() {
            return [OptionalInt.of(1), OptionalInt.empty(), OptionalInt.of(3)]
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_NON_NULL)
    @GraphQLRootResolver
    static class NonNullQuery {
        OptionalInt count() {
            return OptionalInt.of(1)
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_NON_NULL_ELEMENT)
    @GraphQLRootResolver
    static class NonNullElementQuery {
        List<OptionalInt> counts() {
            return []
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_WRONG_CLASS)
    @GraphQLRootResolver
    static class WrongClassQuery {
        OptionalLong count() {
            return OptionalLong.of(1L)
        }
    }

}
