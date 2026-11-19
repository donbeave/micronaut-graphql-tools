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
package example.rename

import graphql.GraphQL
import graphql.schema.GraphQLSchema
import io.micronaut.context.ApplicationContext
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import spock.lang.Specification

// The Groovy suite runs on the JVM: a field bound to a member with another name.
class RenameSpec extends Specification {

    static final String PREFIXES = 'micronaut.graphql.tools.resolver-accessor-prefixes'

    private static Object run(Map<String, Object> properties, String environment, String query) {
        ApplicationContext context = ApplicationContext.run(properties, environment)
        try {
            def result = GraphQL.newGraphQL(context.getBean(GraphQLSchema)).build().execute(query)
            assert result.errors.isEmpty(): result.errors.toString()
            return result.getData()
        } finally {
            context.close()
        }
    }

    private static Throwable failure(Map<String, Object> properties, String environment) {
        try {
            run(properties, environment, '{ __typename }')
            return null
        } catch (Exception e) {
            Throwable cause = e
            while (!(cause instanceof SchemaBindingException) && cause.cause != null && !cause.cause.is(cause)) {
                cause = cause.cause
            }
            return cause
        }
    }

    void 'a field named like a keyword binds to a member with another name'() {
        expect:
        run([:], RenameFixtures.MEMBERS, '{ parcel { id class hashCode headline tagline new(prefix: "a") } }') ==
                [parcel: [id: '1', class: 'parcel', hashCode: 42, headline: 'Fragile', tagline: 'Handle with care', new: 'a parcel']]
    }

    void 'a renamed member and another member for one field fail the startup'() {
        when:
        def e = failure([:], RenameFixtures.CLASH)

        then:
        e instanceof SchemaBindingException
        e.message.startsWith('[multiple-methods-found]')
        e.message.contains('GraphQL coordinate: Slip.title')
    }

    void 'a coordinate binds a resolver method with another name'() {
        expect:
        run([:], RenameFixtures.COORDINATES, '{ default parcel { id class } }') ==
                [default: 'plain', parcel: [id: '1', class: 'parcel']]
    }

    void 'the accessor prefixes bind getX and isX resolver methods when they are on'() {
        expect:
        run([(PREFIXES): true], RenameFixtures.PREFIXES, '{ stamp parcel { id author active } }') ==
                [stamp: 'stamped', parcel: [id: '1', author: 'Jane', active: true]]
        failure([:], RenameFixtures.PREFIXES).message.contains('[field-not-bound]')
    }

}
