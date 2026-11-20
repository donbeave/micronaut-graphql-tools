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
package io.micronaut.graphql.tools.mapping.scalar

import com.tailrocks.graphql.datetime.LocalDateScalar
import com.tailrocks.graphql.datetime.LocalDateTimeScalar
import com.tailrocks.graphql.datetime.YearMonthScalar
import graphql.scalars.ExtendedScalars
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.mapping.GraphQLScalarMapping
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class CustomScalarsSpec extends AbstractTest {

    static final String SPEC_NAME = "CustomScalarsSpec"

    static final LocalDate DATE = LocalDate.of(2026, 12, 24)
    static final LocalDateTime DATE_TIME = LocalDateTime.of(2026, 12, 24, 10, 15, 30)
    static final YearMonth MONTH = YearMonth.of(2026, 12)

    @Language("GraphQL")
    static final String SCHEMA = """
scalar JSON
scalar LocalDate
scalar LocalDateTime
scalar YearMonth

schema {
  query: Query
}

type Query {
  event: Event
  echoDate(date: LocalDate!): LocalDate!
  echoDates(dates: [LocalDate!]!): String!
  echoDateTime(dateTime: LocalDateTime!): LocalDateTime!
  echoMonth(month: YearMonth): YearMonth
  echoJson(data: JSON!): JSON!
  echoAnyJson(data: JSON): JSON
  describe(period: PeriodInput!): String!
}

type Event {
  date: LocalDate!
  createdAt: LocalDateTime!
  month: YearMonth
  data: JSON
  rawData: JSON
  dates: [LocalDate!]!
}

input PeriodInput {
  from: LocalDate!
  until: LocalDateTime
  month: YearMonth
  meta: JSON
  days: [LocalDate!]
}
"""

    void "custom scalars on fields are serialised by the registered scalar implementations"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = executeQuery("""
{
    event {
        date
        createdAt
        month
        data
        rawData
        dates
    }
}
""")

        then:
        result.errors.isEmpty()
        result.data.event.date == '2026-12-24'
        result.data.event.createdAt == '2026-12-24T10:15:30Z'
        result.data.event.month == '2026-12'
        result.data.event.data == [ph: 5.5, tags: ['a', 'b']]
        result.data.event.rawData == '{"ph":5.5}'
        result.data.event.dates == ['2026-12-24', '2027-01-01']
    }

    void "custom scalars as arguments, from literals and from variables"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = executeQuery("""
{
    echoDate(date: "2026-12-24")
    echoDates(dates: ["2026-12-24", "2027-01-01"])
    echoDateTime(dateTime: "2026-12-24T10:15:30Z")
    echoMonth(month: "2026-12")
    echoJson(data: {ph: 5.5, tags: ["a", "b"]})
    echoAnyJson(data: [1, "two"])
}
""")

        then:
        result.errors.isEmpty()
        result.data.echoDate == '2026-12-24'
        result.data.echoDates == 'LocalDate:2026-12-24 LocalDate:2027-01-01'
        result.data.echoDateTime == '2026-12-24T10:15:30Z'
        result.data.echoMonth == '2026-12'
        result.data.echoJson == [ph: 5.5, tags: ['a', 'b']]
        result.data.echoAnyJson == [1, 'two']

        when:
        result = graphQLBean.execute(graphql.ExecutionInput.newExecutionInput()
                .query('query ($d: LocalDate!, $t: LocalDateTime!, $m: YearMonth, $j: JSON!) '
                        + '{ echoDate(date: $d) echoDateTime(dateTime: $t) echoMonth(month: $m) echoJson(data: $j) }')
                .variables([d: '2026-12-24', t: '2026-12-24T10:15:30', m: null, j: [ph: 5.5]])
                .build())

        then:
        result.errors.isEmpty()
        result.data.echoDate == '2026-12-24'
        result.data.echoDateTime == '2026-12-24T10:15:30Z'
        result.data.echoMonth == null
        result.data.echoJson == [ph: 5.5]
    }

    void "custom scalars as input object fields reach the input class as the values the scalars parsed"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = executeQuery("""
{
    describe(period: {
        from: "2026-12-24",
        until: "2026-12-24T10:15:30Z",
        month: "2026-12",
        meta: {source: "test", weight: 2},
        days: ["2026-12-24", "2027-01-01"]
    })
}
""")

        then:
        result.errors.isEmpty()
        result.data.describe == 'from=2026-12-24 (LocalDate) until=2026-12-24T10:15:30 (LocalDateTime) ' +
                'month=2026-12 (YearMonth) meta=[source:test, weight:2] days=[2026-12-24, 2027-01-01] (LocalDate)'
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Factory
    static class Scalars {

        @Bean
        @Singleton
        GraphQLScalarMapping localDate() {
            return GraphQLScalarMapping.of(LocalDateScalar.create(null, false, null), LocalDate)
        }

        @Bean
        @Singleton
        GraphQLScalarMapping localDateTime() {
            return GraphQLScalarMapping.of(LocalDateTimeScalar.create(null, false, null), LocalDateTime)
        }

        @Bean
        @Singleton
        GraphQLScalarMapping yearMonth() {
            return GraphQLScalarMapping.of(YearMonthScalar.create(null), YearMonth)
        }

        @Bean
        @Singleton
        GraphQLScalarMapping json() {
            return GraphQLScalarMapping.builder(ExtendedScalars.Json)
                    .outputClasses(Object)
                    .inputClasses(Object, Map)
                    .build()
        }

    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @GraphQLRootResolver
    static class Query {

        Event event() {
            return new Event()
        }

        LocalDate echoDate(LocalDate date) {
            return date
        }

        String echoDates(List<LocalDate> dates) {
            return dates.collect { "${it.class.simpleName}:${it}" }.join(' ')
        }

        LocalDateTime echoDateTime(LocalDateTime dateTime) {
            return dateTime
        }

        YearMonth echoMonth(YearMonth month) {
            return month
        }

        Object echoJson(Map data) {
            return data
        }

        Object echoAnyJson(Object data) {
            return data
        }

        String describe(PeriodInput period) {
            return "from=${period.from} (${period.from.class.simpleName}) " +
                    "until=${period.until} (${period.until.class.simpleName}) " +
                    "month=${period.month} (${period.month.class.simpleName}) " +
                    "meta=${period.meta} " +
                    "days=${period.days} (${period.days[0].class.simpleName})"
        }

    }

    @GraphQLType
    static class Event {
        LocalDate date = DATE
        LocalDateTime createdAt = DATE_TIME
        YearMonth month = MONTH
        Map<String, Object> data = [ph: 5.5, tags: ['a', 'b']]
        String rawData = '{"ph":5.5}'
        List<LocalDate> dates = [DATE, LocalDate.of(2027, 1, 1)]
    }

    @GraphQLInput
    static class PeriodInput {
        LocalDate from
        LocalDateTime until
        YearMonth month
        Object meta
        List<LocalDate> days
    }

}
