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
package io.micronaut.graphql.tools.mapping.rename

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLField
import io.micronaut.graphql.tools.annotation.GraphQLIgnore
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.binding.BindingCode
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import org.intellij.lang.annotations.Language

class FieldRenameSpec extends AbstractTest {

    static final String SPEC_NAME = "FieldRenameSpec"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  parcel: Parcel
  default: String!
}

type Parcel {
  id: ID!
  class: String!
  hashCode: Int!
  headline: String!
  tagline: String!
  new(prefix: String!): String!
}
"""

    @Requires(property = 'spec.name', pattern = SPEC_NAME + '\\.(members|clash|ignored)')
    @GraphQLRootResolver
    static class ParcelQuery {
        Parcel parcel() {
            return new Parcel(id: '1', title: 'Fragile', subtitle: 'Handle with care')
        }
    }

    @GraphQLType
    static class Parcel {
        String id

        // a Groovy property: the annotation lands on the field
        @GraphQLField("headline")
        String title

        // an explicit getter
        private String subtitle

        @GraphQLField("tagline")
        String getSubtitle() {
            return subtitle
        }

        void setSubtitle(String subtitle) {
            this.subtitle = subtitle
        }

        @GraphQLField("class")
        String kind() {
            return 'parcel'
        }

        @GraphQLField("hashCode")
        int digest() {
            return 42
        }

        @GraphQLField("new")
        String create(String prefix) {
            return prefix + ' parcel'
        }
    }

    void "a member binds the field named by @GraphQLField, whatever its own name is"() {
        given:
        startContext(SCHEMA.replace('  default: String!\n', ''), SPEC_NAME + ".members")

        when:
        def result = executeQuery('{ parcel { id class hashCode headline tagline new(prefix: "a") } }')

        then:
        result.errors.isEmpty()
        result.data.parcel == [id: '1', class: 'parcel', hashCode: 42, headline: 'Fragile', tagline: 'Handle with care', new: 'a parcel']
    }

    @Language("GraphQL")
    static final String SMALL_SCHEMA = """
schema {
  query: Query
}

type Query {
  slip: Slip
}

type Slip {
  title: String!
}
"""

    @Requires(property = 'spec.name', value = SPEC_NAME + ".clash")
    @GraphQLRootResolver
    static class ClashQuery {
        ClashSlip slip() {
            return new ClashSlip()
        }
    }

    @GraphQLType
    static class ClashSlip {
        String title = 'title'

        @GraphQLField("title")
        String heading() {
            return 'heading'
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".twoRenamed")
    @GraphQLRootResolver
    static class TwoRenamedQuery {
        TwoRenamedSlip slip() {
            return new TwoRenamedSlip()
        }
    }

    @GraphQLType
    static class TwoRenamedSlip {
        @GraphQLField("title")
        String first() {
            return 'first'
        }

        @GraphQLField("title")
        String second() {
            return 'second'
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".renamedAway")
    @GraphQLRootResolver
    static class RenamedAwayQuery {
        RenamedAwaySlip slip() {
            return new RenamedAwaySlip()
        }
    }

    @GraphQLType
    static class RenamedAwaySlip {
        @GraphQLField("subject")
        String title = 'title'
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".ignored")
    @GraphQLRootResolver
    static class IgnoredQuery {
        IgnoredSlip slip() {
            return new IgnoredSlip()
        }
    }

    @GraphQLType
    static class IgnoredSlip {
        @GraphQLIgnore
        @GraphQLField("title")
        String heading() {
            return 'heading'
        }
    }

    void "a renamed member that clashes with another member of the field fails with the coordinate"() {
        given:
        startContext(SMALL_SCHEMA, SPEC_NAME + ".clash")

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.code() == BindingCode.MULTIPLE_METHODS_FOUND
        e.cause.message.contains("GraphQL coordinate: Slip.title (line 11, column 3)")
        e.cause.message.contains("${ClashSlip.name} getTitle()")
        e.cause.message.contains("${ClashSlip.name} heading()")
    }

    void "two members renamed to one field fail with the coordinate"() {
        given:
        startContext(SMALL_SCHEMA, SPEC_NAME + ".twoRenamed")

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause.code() == BindingCode.MULTIPLE_METHODS_FOUND
        e.cause.message.contains("GraphQL coordinate: Slip.title (line 11, column 3)")
        e.cause.message.contains("${TwoRenamedSlip.name} first()")
        e.cause.message.contains("${TwoRenamedSlip.name} second()")
    }

    void "a renamed member no longer binds the field of its own name"() {
        given:
        startContext(SMALL_SCHEMA, SPEC_NAME + ".renamedAway")

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause.code() == BindingCode.FIELD_NOT_BOUND
        e.cause.message.contains("GraphQL coordinate: Slip.title")
    }

    void "a renamed member marked @GraphQLIgnore binds nothing"() {
        given:
        startContext(SMALL_SCHEMA, SPEC_NAME + ".ignored")

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause.code() == BindingCode.FIELD_NOT_BOUND
        e.cause.message.contains("GraphQL coordinate: Slip.title")
    }

    @Requires(property = 'spec.name', pattern = SPEC_NAME + '\\.(coordinates|duplicateCoordinate)')
    @GraphQLRootResolver
    static class CoordinateQuery {
        @GraphQLSchemaCoordinate("Query.parcel")
        CoordinateParcel fetchParcel() {
            return new CoordinateParcel(id: '1')
        }

        @GraphQLSchemaCoordinate("Query.default")
        String defaultValue() {
            return 'plain'
        }
    }

    @GraphQLType
    static class CoordinateParcel {
        String id
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".coordinates")
    @GraphQLTypeResolver(CoordinateParcel.class)
    static class CoordinateParcelResolver {
        @GraphQLSchemaCoordinate("Parcel.id")
        String identity(CoordinateParcel parcel) {
            return parcel.id
        }

        @GraphQLSchemaCoordinate("Parcel.class")
        String kind(CoordinateParcel parcel) {
            return 'parcel'
        }

        @GraphQLSchemaCoordinate("Parcel.hashCode")
        int digest(CoordinateParcel parcel) {
            return 42
        }

        @GraphQLSchemaCoordinate("Parcel.new")
        String create(CoordinateParcel parcel, String prefix) {
            return prefix + ' parcel'
        }

        @GraphQLSchemaCoordinate("Parcel.headline")
        String title(CoordinateParcel parcel) {
            return 'Fragile'
        }

        @GraphQLSchemaCoordinate("Parcel.tagline")
        String subtitle(CoordinateParcel parcel) {
            return 'Handle with care'
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".duplicateCoordinate")
    @GraphQLTypeResolver(CoordinateParcel.class)
    static class DuplicateCoordinateResolver {
        @GraphQLSchemaCoordinate("Parcel.class")
        String kind(CoordinateParcel parcel) {
            return 'parcel'
        }

        @GraphQLSchemaCoordinate("Parcel.class")
        String type(CoordinateParcel parcel) {
            return 'other'
        }
    }

    void "a coordinate binds a method with another name to the root and the type field it names"() {
        given:
        startContext(SCHEMA, SPEC_NAME + ".coordinates")

        when:
        def result = executeQuery('{ default parcel { id class hashCode headline tagline new(prefix: "a") } }')

        then:
        result.errors.isEmpty()
        result.data.default == 'plain'
        result.data.parcel == [id: '1', class: 'parcel', hashCode: 42, headline: 'Fragile', tagline: 'Handle with care', new: 'a parcel']
    }

    void "two methods that declare one coordinate fail with the coordinate"() {
        given:
        startContext(SCHEMA.replaceAll(/(?m)^  (hashCode|headline|tagline|new|id).*\n/, ''), SPEC_NAME + ".duplicateCoordinate")

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause.code() == BindingCode.MULTIPLE_METHODS_FOUND
        e.cause.message.contains("GraphQL coordinate: Parcel.class")
        e.cause.message.contains("${DuplicateCoordinateResolver.name} kind(${CoordinateParcel.name} parcel)")
        e.cause.message.contains("${DuplicateCoordinateResolver.name} type(${CoordinateParcel.name} parcel)")
    }

    @Language("GraphQL")
    static final String PREFIX_SCHEMA = """
schema {
  query: Query
}

type Query {
  ping: String!
  parcel: Parcel
}

type Parcel {
  author: String!
  active: Boolean!
}
"""

    @Requires(property = 'spec.name', pattern = SPEC_NAME + '\\.(prefixes|prefixesOff|prefixClash)')
    @GraphQLRootResolver
    static class PrefixQuery {
        String getPing() {
            return 'pong'
        }

        PrefixParcel parcel() {
            return new PrefixParcel()
        }
    }

    @GraphQLType
    static class PrefixParcel {
    }

    @Requires(property = 'spec.name', pattern = SPEC_NAME + '\\.(prefixes|prefixesOff)')
    @GraphQLTypeResolver(PrefixParcel.class)
    static class PrefixParcelResolver {
        String getAuthor(PrefixParcel parcel) {
            return 'Jane'
        }

        boolean isActive(PrefixParcel parcel) {
            return true
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME + ".prefixClash")
    @GraphQLTypeResolver(PrefixParcel.class)
    static class PrefixClashResolver {
        String author(PrefixParcel parcel) {
            return 'Jane'
        }

        String getAuthor(PrefixParcel parcel) {
            return 'John'
        }

        boolean isActive(PrefixParcel parcel) {
            return true
        }
    }

    void "with the accessor prefixes on, getX and isX resolver methods bind the field x"() {
        given:
        startContext(PREFIX_SCHEMA, SPEC_NAME + ".prefixes", ['micronaut.graphql.tools.resolver-accessor-prefixes': true])

        when:
        def result = executeQuery('{ ping parcel { author active } }')

        then:
        result.errors.isEmpty()
        result.data.ping == 'pong'
        result.data.parcel == [author: 'Jane', active: true]
    }

    void "with the accessor prefixes off, a getX resolver method does not bind the field x"() {
        given:
        startContext(PREFIX_SCHEMA, SPEC_NAME + ".prefixesOff")

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.message.contains("[field-not-bound] The method `ping` not found in any root resolvers")
        e.cause.message.contains("GraphQL coordinate: Parcel.author")
    }

    void "with the accessor prefixes on, author and getAuthor for one field fail with the coordinate"() {
        given:
        startContext(PREFIX_SCHEMA, SPEC_NAME + ".prefixClash", ['micronaut.graphql.tools.resolver-accessor-prefixes': true])

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause.code() == BindingCode.MULTIPLE_METHODS_FOUND
        e.cause.message.contains("GraphQL coordinate: Parcel.author")
    }
}
