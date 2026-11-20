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
package example.models

import graphql.GraphQL
import graphql.schema.GraphQLSchema
import io.micronaut.context.ApplicationContext
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import spock.lang.Specification

// The Groovy suite runs on the JVM: classes without an annotation bind through the introspections that @GraphQLModels generates.
class ExternalModelSpec extends Specification {

    private static Object run(String environment, String query) {
        ApplicationContext context = ApplicationContext.run([:], environment)
        try {
            def result = GraphQL.newGraphQL(context.getBean(GraphQLSchema)).build().execute(query)
            assert result.errors.isEmpty(): result.errors.toString()
            return result.getData()
        } finally {
            context.close()
        }
    }

    private static Throwable failure(String environment) {
        try {
            run(environment, '{ __typename }')
            return null
        } catch (Exception e) {
            Throwable cause = e
            while (!(cause instanceof SchemaBindingException) && cause.cause != null && !cause.cause.is(cause)) {
                cause = cause.cause
            }
            return cause
        }
    }

    void 'listed classes bind without any other annotation'() {
        expect:
        run(ExternalModelFixtures.CLASSES, '{ atlas { title region { name } pages } atlases { title } }') ==
                [atlas  : [title: 'World', region: [name: 'Europe'], pages: 320],
                 atlases: [[title: 'World'], [title: 'Roads']]]
    }

    void 'the classes of a listed package bind'() {
        expect:
        run(ExternalModelFixtures.PACKAGE, '{ chart { label axis { unit } } }') ==
                [chart: [label: 'Sales', axis: [unit: 'EUR']]]
    }

    void 'a returned class that nothing lists fails the startup with the class and the field'() {
        when:
        def e = failure(ExternalModelFixtures.MISSING)

        then:
        e instanceof SchemaBindingException
        e.message.startsWith('[class-not-introspected]')
        e.message.contains('example.external.unlisted.Unlisted')
        e.message.contains('GraphQL coordinate: Query.unlisted')
        e.message.contains('@GraphQLModels')
    }

    void 'a class reached through a property that nothing lists fails the startup with the field'() {
        when:
        def e = failure(ExternalModelFixtures.NESTED)

        then:
        e instanceof SchemaBindingException
        e.message.startsWith('[class-not-introspected]')
        e.message.contains('example.external.unlisted.Inner')
        e.message.contains('Holder.inner')
    }

}
