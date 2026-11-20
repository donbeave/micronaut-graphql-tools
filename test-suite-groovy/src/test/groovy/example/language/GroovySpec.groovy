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
package example.language

import graphql.ExecutionResult
import graphql.GraphQL
import graphql.schema.GraphQLSchema
import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

// The Groovy suite runs on the JVM: properties that Groovy generates, GString results, and sealed types.
class GroovySpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run([:], GroovyFixtures.LANGUAGE)

    private ExecutionResult execute(String query) {
        return GraphQL.newGraphQL(context.getBean(GraphQLSchema)).build().execute(query)
    }

    private Object run(String query) {
        def result = execute(query)
        assert result.errors.isEmpty(): result.errors.toString()
        return result.getData()
    }

    void 'generated properties bind as getters'() {
        expect:
        run('{ member { name age active nickname greeting } }') ==
                [member: [name: 'Ada', age: 36, active: true, nickname: null, greeting: 'Hello, Ada']]
    }

    void 'generated properties bind as input setters'() {
        expect:
        run('{ echoMember(input: { name: "Bo", age: 5, active: false }) { name age active } }') ==
                [echoMember: [name: 'Bo', age: 5, active: false]]
    }

    void 'an input with a constructor binds through it'() {
        expect:
        run('{ describe(input: { latitude: 1.5, longitude: 2.5 }) }') == [describe: '1.5,2.5']
    }

    void 'a GString result serializes as a string'() {
        expect:
        run('{ greet(name: "Bo") }') == [greet: 'Hello, Bo']
    }

    void 'a CompletionStage of GString serializes as a string'() {
        expect:
        run('{ greetLater(name: "Bo") }') == [greetLater: 'Hello, Bo']
    }

    void '@ExecuteOn on a Groovy bean runs the resolver on the named executor'() {
        when:
        String value = run('{ greetLaterOnExecutor(name: "Bo") }').greetLaterOnExecutor

        then:
        value.endsWith(':Bo')
        value.startsWith('io-executor')
    }

    void 'a sealed union resolves its members'() {
        expect:
        run('{ shapes { __typename ... on Circle { radius } ... on Square { side } } }') ==
                [shapes: [[__typename: 'Circle', radius: 2], [__typename: 'Square', side: 3]]]
    }

    void 'a sealed interface resolves its implementations'() {
        expect:
        run('{ animals { __typename name ... on Dog { tricks } ... on Cat { lives } } }') ==
                [animals: [[__typename: 'Dog', name: 'Rex', tricks: 4], [__typename: 'Cat', name: 'Tom', lives: 9]]]
    }
}
