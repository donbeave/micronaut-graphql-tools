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

import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.language.IntValue
import graphql.language.StringValue
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import io.micronaut.graphql.tools.scalars.internal.time.TimeScalars
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.Period
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** A table of values for each java.time scalar, with the default format and with a configured one. */
class JavaTimeScalarsSpec extends Specification {

    static final GraphQLContext CONTEXT = GraphQLContext.getDefault()
    static final Instant INSTANT = Instant.parse('2026-12-24T10:15:30Z')

    @Unroll
    void "#scalar with the format #format writes #wire and reads it back"() {
        given:
        def coercing = TimeScalars.coercing(scalar, scalar.defaultName(), format)

        expect:
        coercing.serialize(value, CONTEXT, Locale.ROOT) == wire
        coercing.parseValue(wire, CONTEXT, Locale.ROOT) == value
        if (wire instanceof String) {
            assert coercing.parseLiteral(StringValue.of(wire), CoercedVariables.emptyVariables(), CONTEXT, Locale.ROOT) == value
        } else {
            assert coercing.parseLiteral(new IntValue(BigInteger.valueOf((long) wire)), CoercedVariables.emptyVariables(), CONTEXT, Locale.ROOT) == value
        }

        where:
        scalar                                 | format                    | value                                                                  | wire
        StandardScalar.INSTANT                 | null                      | INSTANT                                                                | '2026-12-24T10:15:30Z'
        StandardScalar.INSTANT                 | 'iso'                     | Instant.parse('2026-12-24T10:15:30.250Z')                              | '2026-12-24T10:15:30.250Z'
        StandardScalar.INSTANT                 | 'epoch-seconds'           | Instant.ofEpochSecond(1_700_000_000L)                                  | 1_700_000_000L
        StandardScalar.INSTANT                 | 'epoch-millis'            | Instant.ofEpochMilli(1_700_000_000_123L)                               | 1_700_000_000_123L
        StandardScalar.INSTANT                 | 'yyyy-MM-dd HH:mm:ss'     | INSTANT                                                                | '2026-12-24 10:15:30'
        StandardScalar.LOCAL_DATE              | null                      | LocalDate.of(2026, 12, 24)                                             | '2026-12-24'
        StandardScalar.LOCAL_DATE              | 'dd/MM/yyyy'              | LocalDate.of(2026, 12, 24)                                             | '24/12/2026'
        StandardScalar.LOCAL_DATE              | 'yyyyMMdd'                | LocalDate.of(2026, 1, 2)                                               | '20260102'
        StandardScalar.LOCAL_TIME              | null                      | LocalTime.of(10, 15, 30)                                               | '10:15:30'
        StandardScalar.LOCAL_TIME              | 'HH.mm'                   | LocalTime.of(10, 15)                                                   | '10.15'
        StandardScalar.LOCAL_DATE_TIME         | null                      | LocalDateTime.of(2026, 12, 24, 10, 15, 30)                             | '2026-12-24T10:15:30'
        StandardScalar.LOCAL_DATE_TIME         | 'yyyy-MM-dd HH:mm:ss'     | LocalDateTime.of(2026, 12, 24, 10, 15, 30)                             | '2026-12-24 10:15:30'
        StandardScalar.OFFSET_TIME             | null                      | OffsetTime.of(10, 15, 30, 0, ZoneOffset.ofHours(1))                    | '10:15:30+01:00'
        StandardScalar.OFFSET_TIME             | 'HH:mm XXX'               | OffsetTime.of(10, 15, 0, 0, ZoneOffset.ofHours(1))                     | '10:15 +01:00'
        StandardScalar.OFFSET_DATE_TIME        | null                      | OffsetDateTime.of(2026, 12, 24, 10, 15, 30, 0, ZoneOffset.ofHours(1))  | '2026-12-24T10:15:30+01:00'
        StandardScalar.OFFSET_DATE_TIME        | 'yyyy-MM-dd HH:mm:ssXXX'  | OffsetDateTime.of(2026, 12, 24, 10, 15, 30, 0, ZoneOffset.UTC)         | '2026-12-24 10:15:30Z'
        StandardScalar.ZONED_DATE_TIME         | null                      | ZonedDateTime.of(2026, 12, 24, 10, 15, 30, 0, ZoneId.of('Europe/Paris')) | '2026-12-24T10:15:30+01:00[Europe/Paris]'
        StandardScalar.ZONED_DATE_TIME         | "yyyy-MM-dd HH:mm VV"     | ZonedDateTime.of(2026, 12, 24, 10, 15, 0, 0, ZoneId.of('Europe/Paris')) | '2026-12-24 10:15 Europe/Paris'
        StandardScalar.YEAR_MONTH              | null                      | YearMonth.of(2026, 12)                                                 | '2026-12'
        StandardScalar.YEAR_MONTH              | 'MM/yyyy'                 | YearMonth.of(2026, 12)                                                 | '12/2026'
        StandardScalar.YEAR                    | null                      | Year.of(2026)                                                          | '2026'
        StandardScalar.YEAR                    | 'yyyy'                    | Year.of(2026)                                                          | '2026'
        StandardScalar.DURATION                | null                      | Duration.ofMinutes(90)                                                 | 'PT1H30M'
        StandardScalar.DURATION                | 'seconds'                 | Duration.ofSeconds(90)                                                 | 90L
        StandardScalar.DURATION                | 'millis'                  | Duration.ofMillis(1500)                                                | 1500L
        StandardScalar.PERIOD                  | null                      | Period.of(1, 2, 3)                                                     | 'P1Y2M3D'
    }

    @Unroll
    void "#scalar rejects the input #input"() {
        given:
        def coercing = TimeScalars.coercing(scalar, scalar.defaultName(), format)

        when:
        coercing.parseValue(input, CONTEXT, Locale.ROOT)

        then:
        def e = thrown(CoercingParseValueException)
        e.message.startsWith(scalar.defaultName() + ': ')

        where:
        scalar                          | format          | input
        StandardScalar.LOCAL_DATE       | null            | 'tomorrow'
        StandardScalar.LOCAL_DATE       | null            | '2026-13-01'
        StandardScalar.LOCAL_DATE       | null            | '2026-02-30'
        StandardScalar.LOCAL_DATE       | null            | '24/12/2026'
        StandardScalar.LOCAL_DATE       | null            | 20261224
        StandardScalar.LOCAL_DATE       | null            | ''
        StandardScalar.LOCAL_DATE       | 'dd/MM/yyyy'    | '2026-12-24'
        StandardScalar.LOCAL_TIME       | null            | '25:00:00'
        StandardScalar.LOCAL_DATE_TIME  | null            | '2026-12-24'
        StandardScalar.OFFSET_DATE_TIME | null            | '2026-12-24T10:15:30'
        StandardScalar.ZONED_DATE_TIME  | null            | '2026-12-24T10:15:30+01:00[Nowhere/Land]'
        StandardScalar.INSTANT          | null            | '2026-12-24T10:15:30+01:00x'
        StandardScalar.INSTANT          | 'epoch-millis'  | '1700000000000'
        StandardScalar.INSTANT          | 'epoch-millis'  | 1.5d
        StandardScalar.INSTANT          | 'epoch-seconds' | Long.MAX_VALUE
        StandardScalar.YEAR_MONTH       | null            | '2026-1'
        StandardScalar.YEAR             | null            | 'year'
        StandardScalar.DURATION         | null            | '90 minutes'
        StandardScalar.DURATION         | 'seconds'       | 'PT1M'
        StandardScalar.PERIOD           | null            | 'P1X'
    }

    void "a literal of the wrong kind and a value that is not the Java type fail with the exception of their phase"() {
        given:
        def coercing = TimeScalars.coercing(StandardScalar.LOCAL_DATE, 'LocalDate', null)

        when:
        coercing.parseLiteral(IntValue.of(5), CoercedVariables.emptyVariables(), CONTEXT, Locale.ROOT)

        then:
        thrown(CoercingParseLiteralException)

        when:
        coercing.serialize('2026-12-24', CONTEXT, Locale.ROOT)

        then:
        def e = thrown(CoercingSerializeException)
        e.message.contains('java.time.LocalDate')
    }

    void "a pattern that cannot write the Java type fails when a value is written"() {
        when:
        TimeScalars.coercing(StandardScalar.LOCAL_DATE, 'LocalDate', 'HH:mm').serialize(LocalDate.of(2026, 12, 24), CONTEXT, Locale.ROOT)

        then:
        thrown(CoercingSerializeException)
    }

    void "the date fast path agrees with the ISO parser for expanded and unusual years"() {
        given:
        def coercing = TimeScalars.coercing(StandardScalar.LOCAL_DATE, 'LocalDate', null)

        expect:
        coercing.parseValue(text, CONTEXT, Locale.ROOT) == LocalDate.parse(text)
        coercing.serialize(coercing.parseValue(text, CONTEXT, Locale.ROOT), CONTEXT, Locale.ROOT) == text

        where:
        text << ['0001-01-01', '9999-12-31', '+10000-01-01', '-0001-01-01', '2024-02-29']
    }

    void "the old coercing methods agree with the new ones"() {
        given:
        def coercing = TimeScalars.coercing(StandardScalar.LOCAL_DATE, 'LocalDate', null)

        expect:
        coercing.serialize(LocalDate.of(2026, 12, 24)) == '2026-12-24'
        coercing.parseValue('2026-12-24') == LocalDate.of(2026, 12, 24)
        coercing.parseLiteral(StringValue.of('2026-12-24')) == LocalDate.of(2026, 12, 24)
        coercing.valueToLiteral(LocalDate.of(2026, 12, 24)) instanceof StringValue
    }

    void "a format that a scalar does not have is rejected when the coercion is built"() {
        when:
        TimeScalars.coercing(scalar, scalar.defaultName(), format)

        then:
        thrown(IllegalArgumentException)

        where:
        scalar                    | format
        StandardScalar.PERIOD     | 'dd'
        StandardScalar.DURATION   | 'HH:mm'
        StandardScalar.LOCAL_DATE | 'not a pattern {'
    }

}
