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
package example.scalars

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLSchema
import io.micronaut.context.ApplicationContext
import io.micronaut.json.JsonMapper
import spock.lang.Specification

// The Groovy suite runs on the JVM: the standard scalars of the scalars module, from the schema to the JSON of the response.
class ScalarsTest extends Specification {

    private static ExecutionResult execute(ApplicationContext context, String query, Map<String, Object> variables = [:]) {
        GraphQL.newGraphQL(context.getBean(GraphQLSchema)).build()
                .execute(ExecutionInput.newExecutionInput().query(query).variables(variables).build())
    }

    private static String wire(ApplicationContext context, ExecutionResult result) {
        context.getBean(JsonMapper).writeValueAsString(result.toSpecification())
    }

    // tag::round-trip[]
    void 'every scalar round-trips with its default format'() {
        given:
        ApplicationContext context = ApplicationContext.run([:], ScalarsFixtures.DEFAULTS)

        when:
        def result = execute(context, 'query ($t: OffsetDateTime!, $j: JSON) {' +
                ' bookedOn(date: "2026-12-24")' +
                ' bookedAt(time: $t)' +
                ' elapsed(spent: "PT1H30M")' +
                ' payload(data: $j)' +
                ' ticket(id: "123e4567-e89b-12d3-a456-426614174000")' +
                ' homepage(url: "https://example.org/a?b=c") }',
                [t: '2026-12-24T10:15:30+01:00', j: [kind: 'x']])

        then:
        result.errors.isEmpty()
        wire(context, result) == '{"data":{"bookedOn":"2026-12-25","bookedAt":"2026-12-24T11:15:30+01:00",' +
                '"elapsed":"PT2H","payload":{"kind":"x"},' +
                '"ticket":"123e4567-e89b-12d3-a456-426614174000",' +
                '"homepage":"https://example.org/a?b=c"}}'

        cleanup:
        context?.close()
    }
    // end::round-trip[]

    // tag::renamed[]
    void 'a configured name and format change the type that binds'() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'micronaut.graphql.tools.scalars.local-date.name'  : 'Date',
                'micronaut.graphql.tools.scalars.local-date.format': 'dd/MM/yyyy',
        ], ScalarsFixtures.RENAMED)

        when:
        def schema = context.getBean(GraphQLSchema)
        def result = execute(context, '{ bookedOn(date: "24/12/2026") }')

        then:
        schema.getType('Date') instanceof GraphQLScalarType
        schema.getType('LocalDate') == null
        result.errors.isEmpty()
        result.getData() == [bookedOn: '25/12/2026']

        cleanup:
        context?.close()
    }
    // end::renamed[]

    // tag::configured[]
    void 'a scalar can be enabled, renamed and disabled by properties'() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'micronaut.graphql.tools.scalars.offset-date-time.enabled': true,
                'micronaut.graphql.tools.scalars.local-date.name'         : 'Date',
                'micronaut.graphql.tools.scalars.uuid.enabled'            : false,
        ], ScalarsFixtures.CONFIGURED)

        when:
        def schema = context.getBean(GraphQLSchema)
        def result = execute(context, '{ bookedOn(date: "2026-12-24")' +
                ' bookedAt(time: "2026-12-24T10:15:30+01:00")' +
                ' ticket(id: "123e4567-e89b-12d3-a456-426614174000") }')

        then:
        schema.getType('Date') instanceof GraphQLScalarType
        schema.getType('LocalDate') == null
        schema.getType('OffsetDateTime') instanceof GraphQLScalarType
        (schema.getType('UUID') as GraphQLScalarType).description == ScalarsFixtures.OWN_UUID
        result.errors.isEmpty()
        result.getData() == [bookedOn: '2026-12-25', bookedAt: '2026-12-24T11:15:30+01:00',
                             ticket  : '123e4567-e89b-12d3-a456-426614174000']

        cleanup:
        context?.close()
    }
    // end::configured[]

    void 'an unknown scalar id fails the startup'() {
        when:
        ApplicationContext.run(['micronaut.graphql.tools.scalars.local-dat.enabled': false], ScalarsFixtures.DEFAULTS).close()

        then:
        def failure = thrown(Exception)
        def messages = generateMessages(failure)
        messages.contains('micronaut.graphql.tools.scalars.local-dat')
        messages.contains('local-date')
    }

    private static String generateMessages(Throwable failure) {
        StringBuilder messages = new StringBuilder()
        for (Throwable t = failure; t != null; t = t.cause) {
            messages.append(t.message).append('\n')
        }
        messages.toString()
    }

    void 'an invalid value is an error that names the scalar'() {
        given:
        ApplicationContext context = ApplicationContext.run([:], ScalarsFixtures.DEFAULTS)

        when:
        def literal = execute(context, '{ bookedOn(date: "next week") }')
        def variable = execute(context, 'query ($d: Duration!) { elapsed(spent: $d) }', [d: '90 minutes'])

        then:
        literal.errors.size() == 1
        literal.errors[0].message.contains('LocalDate')
        variable.errors.size() == 1
        variable.errors[0].message.contains('Duration')

        cleanup:
        context?.close()
    }

    void 'a JSON number that cannot be written is an error that names the scalar'() {
        given:
        ApplicationContext context = ApplicationContext.run([:], ScalarsFixtures.DEFAULTS)

        when:
        def result = execute(context, 'query ($j: JSON) { payload(data: $j) }', [j: value])
        def literal = execute(context, '{ payload(data: {a: 1e10000}) }')

        then:
        result.errors.size() == 1
        result.errors[0].message.contains('JSON')
        literal.errors.size() == 1
        literal.errors[0].message.contains('JSON')

        cleanup:
        context?.close()

        where:
        value << [[a: Double.NaN], [a: [new BigDecimal('1e-10000')]], [new BigDecimal('1e10000')]]
    }

}
