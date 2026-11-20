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
package io.micronaut.graphql.tools.scalars

import jakarta.inject.Singleton
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import org.intellij.lang.annotations.Language

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.YearMonth

/** The scalars of the module bind the schema: the default names, a configured name and format, and a switched-off scalar. */
class ScalarBindingSpec extends AbstractScalarsSpec {

    static final String SPEC_NAME = 'ScalarBindingSpec'
    static final String RENAMED = 'ScalarBindingSpec.renamed'
    static final String MINIMAL = 'ScalarBindingSpec.minimal'

    @Language('GraphQL')
    static final String DEFAULT_SCHEMA = '''
scalar LocalDate
scalar LocalDateTime
scalar OffsetDateTime
scalar Instant
scalar YearMonth
scalar Duration
scalar JSON
scalar UUID
scalar Url
scalar PositiveInt
scalar NonNegativeFloat

schema { query: Query }

type Query {
  event: Event!
  echoDate(date: LocalDate!): LocalDate!
  echoDates(dates: [LocalDate!]!): [LocalDate!]!
  echoDateTime(value: LocalDateTime!): LocalDateTime!
  echoOffset(value: OffsetDateTime!): OffsetDateTime!
  echoInstant(value: Instant!): Instant!
  echoMonth(value: YearMonth): YearMonth
  echoDuration(value: Duration!): Duration!
  echoJson(data: JSON): JSON
  echoUuid(id: UUID!): UUID!
  echoUrl(url: Url!): Url!
  echoCount(count: PositiveInt!): PositiveInt!
  echoPrice(price: NonNegativeFloat!): NonNegativeFloat!
  describe(range: Range!): String!
}

type Event {
  day: LocalDate!
  at: OffsetDateTime!
  payload: JSON
}

input Range {
  from: LocalDate!
  until: LocalDateTime
  month: YearMonth
  meta: JSON
}
'''

    @Language('GraphQL')
    static final String RENAMED_SCHEMA = '''
scalar Date
scalar Stamp

schema { query: Query }

type Query {
  echoDate(date: Date!): Date!
  echoStamp(value: Stamp!): Stamp!
}
'''

    void "each scalar round-trips as output, as a literal and as a variable, with the default names and formats"() {
        given:
        startContext(DEFAULT_SCHEMA, SPEC_NAME)

        when:
        def result = execute('''
{
  event { day at payload }
  echoDate(date: "2026-12-24")
  echoDates(dates: ["2026-12-24", "2027-01-01"])
  echoDateTime(value: "2026-12-24T10:15:30")
  echoOffset(value: "2026-12-24T10:15:30+01:00")
  echoInstant(value: "2026-12-24T10:15:30Z")
  echoMonth(value: "2026-12")
  echoDuration(value: "PT1H30M")
  echoJson(data: {a: 1, b: [true, null, "x", 2.5]})
  echoUuid(id: "123e4567-e89b-12d3-a456-426614174000")
  echoUrl(url: "https://example.org/a?b=c")
  echoCount(count: 3)
  echoPrice(price: 2.5)
}
''')

        then:
        result.errors.isEmpty()
        result.data.event == [day: '2026-12-24', at: '2026-12-24T10:15:30+01:00', payload: [kind: 'x', tags: ['a']]]
        result.data.echoDate == '2026-12-24'
        result.data.echoDates == ['2026-12-24', '2027-01-01']
        result.data.echoDateTime == '2026-12-24T10:15:30'
        result.data.echoOffset == '2026-12-24T10:15:30+01:00'
        result.data.echoInstant == '2026-12-24T10:15:30Z'
        result.data.echoMonth == '2026-12'
        result.data.echoDuration == 'PT1H30M'
        result.data.echoJson == [a: 1, b: [true, null, 'x', 2.5d]]
        result.data.echoUuid == '123e4567-e89b-12d3-a456-426614174000'
        result.data.echoUrl.toString() == 'https://example.org/a?b=c'
        result.data.echoCount == 3
        result.data.echoPrice == 2.5d

        when:
        result = execute('''query ($d: LocalDate!, $t: Instant!, $j: JSON, $m: YearMonth, $du: Duration!) {
  echoDate(date: $d) echoInstant(value: $t) echoJson(data: $j) echoMonth(value: $m) echoDuration(value: $du) }''',
                [d: '2026-12-24', t: '2026-12-24T10:15:30Z', j: [k: [1, 2]], m: null, du: 'PT5S'])

        then:
        result.errors.isEmpty()
        result.data.echoDate == '2026-12-24'
        result.data.echoInstant == '2026-12-24T10:15:30Z'
        result.data.echoJson == [k: [1, 2]]
        result.data.echoMonth == null
        result.data.echoDuration == 'PT5S'
    }

    void "scalars inside an input object reach the input class as Java values"() {
        given:
        startContext(DEFAULT_SCHEMA, SPEC_NAME)

        when:
        def result = execute('{ describe(range: {from: "2026-12-24", until: "2026-12-25T01:02:03", month: "2027-01", meta: {w: 2}}) }')

        then:
        result.errors.isEmpty()
        result.data.describe == 'from=2026-12-24 until=2026-12-25T01:02:03 month=2027-01 meta=[w:2]'
    }

    void "an invalid value is a GraphQL error that names the scalar"() {
        given:
        startContext(DEFAULT_SCHEMA, SPEC_NAME)

        when:
        def literal = execute('{ echoDate(date: "next week") }')
        def variable = execute('query ($d: LocalDate!) { echoDate(date: $d) }', [d: '2026-02-30'])
        def number = execute('{ echoCount(count: 0) }')

        then:
        literal.data == null
        literal.errors.size() == 1
        literal.errors[0].message.contains('LocalDate')
        variable.errors.size() == 1
        variable.errors[0].message.contains('LocalDate')
        number.errors.size() == 1
        number.errors[0].message.contains('PositiveInt')
    }

    void "a configured name and format change the type of the schema that binds"() {
        given:
        startContext(RENAMED_SCHEMA, RENAMED, [
                'micronaut.graphql.tools.scalars.local-date.name'  : 'Date',
                'micronaut.graphql.tools.scalars.local-date.format': 'dd/MM/yyyy',
                'micronaut.graphql.tools.scalars.instant.name'     : 'Stamp',
                'micronaut.graphql.tools.scalars.instant.format'   : 'epoch-millis',
        ])

        when:
        def result = execute('{ echoDate(date: "24/12/2026") echoStamp(value: 1700000000123) }')

        then:
        result.errors.isEmpty()
        result.data.echoDate == '24/12/2026'
        result.data.echoStamp == 1700000000123L
        graphQL.graphQLSchema.getType('Date').name == 'Date'
        graphQL.graphQLSchema.getType('LocalDate') == null

        when:
        def iso = execute('{ echoDate(date: "2026-12-24") }')

        then:
        iso.data == null
        iso.errors.size() == 1
        iso.errors[0].message.contains('Date')
    }

    void "a schema that still uses the default name after a rename fails the startup with the missing mapping"() {
        when:
        startContext(DEFAULT_SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.scalars.local-date.name': 'Date'])
        graphQL

        then:
        def e = thrown(Exception)
        SchemaBindingException found = rootCause(e)
        found.message.contains('[scalar-mapping-not-provided] The GraphQL scalar LocalDate has no mapping')
    }

    void "a scalar that is switched off has no mapping, and the master switch turns every scalar off"() {
        when:
        startContext(DEFAULT_SCHEMA, SPEC_NAME, ['micronaut.graphql.tools.scalars.uuid.enabled': 'false'])
        graphQL

        then:
        def failure = thrown(Exception)
        rootCause(failure).message.contains('[scalar-mapping-not-provided] The GraphQL scalar UUID has no mapping')

        when:
        applicationContext?.close()
        startContext('schema { query: Query } type Query { ok: String }', MINIMAL, ['micronaut.graphql.tools.scalars.enabled': 'false'])

        then:
        applicationContext.getBeansOfType(io.micronaut.graphql.tools.mapping.GraphQLScalarMapping).isEmpty()
    }

    void "the scalars of the module are registered once each, with the names of the enum"() {
        when:
        startContext('schema { query: Query } type Query { ok: String }', MINIMAL)
        def names = applicationContext.getBeansOfType(io.micronaut.graphql.tools.mapping.GraphQLScalarMapping)*.name

        then:
        names.toSorted() == StandardScalar.values()*.defaultName().toSorted()
    }

    private static SchemaBindingException rootCause(Throwable e) {
        while (!(e instanceof SchemaBindingException) && e.cause != null && e.cause != e) {
            e = e.cause
        }
        assert e instanceof SchemaBindingException: e.toString()
        (SchemaBindingException) e
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @GraphQLRootResolver
    static class Query {

        Event event() { new Event() }

        LocalDate echoDate(LocalDate date) { date }

        List<LocalDate> echoDates(List<LocalDate> dates) { dates }

        LocalDateTime echoDateTime(LocalDateTime value) { value }

        OffsetDateTime echoOffset(OffsetDateTime value) { value }

        Instant echoInstant(Instant value) { value }

        YearMonth echoMonth(YearMonth value) { value }

        Duration echoDuration(Duration value) { value }

        Object echoJson(Object data) { data }

        UUID echoUuid(UUID id) { id }

        URL echoUrl(URL url) { url }

        Integer echoCount(Integer count) { count }

        Double echoPrice(Double price) { price }

        String describe(Range range) {
            "from=${range.from} until=${range.until} month=${range.month} meta=${range.meta}"
        }

    }

    @Requires(property = 'spec.name', value = RENAMED)
    @Singleton
    @GraphQLRootResolver
    static class RenamedQuery {

        LocalDate echoDate(LocalDate date) { date }

        Instant echoStamp(Instant value) { value }

    }

    @Requires(property = 'spec.name', value = MINIMAL)
    @Singleton
    @GraphQLRootResolver
    static class MinimalQuery {

        String ok() { 'ok' }

    }

    @GraphQLType
    static class Event {
        LocalDate day = LocalDate.of(2026, 12, 24)
        OffsetDateTime at = OffsetDateTime.parse('2026-12-24T10:15:30+01:00')
        Map<String, Object> payload = [kind: 'x', tags: ['a']]
    }

    @GraphQLInput
    static class Range {
        LocalDate from
        LocalDateTime until
        YearMonth month
        Object meta
    }

}
