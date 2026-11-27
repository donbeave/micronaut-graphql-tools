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
package io.micronaut.graphql.tools.client.internal

import io.micronaut.graphql.tools.client.GraphQLSession.Cookie
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class CookieJarSpec extends Specification {

    static class Ticking extends Clock {
        Instant now = Instant.parse('2030-01-01T00:00:00Z')

        ZoneOffset getZone() { ZoneOffset.UTC }

        Clock withZone(java.time.ZoneId zone) { this }

        Instant instant() { now }
    }

    Ticking clock = new Ticking()
    CookieJar jar = new CookieJar(clock)

    void "a Max-Age cookie is sent until its expiry and then dropped"() {
        when:
        jar.store(['sid=abc; Max-Age=60'], '/graphql')

        then:
        jar.header('/graphql') == 'sid=abc'
        jar.snapshot() == [new Cookie('sid', 'abc', '/', clock.now.plusSeconds(60))]

        when:
        clock.now = clock.now.plusSeconds(59)

        then:
        jar.header('/graphql') == 'sid=abc'

        when:
        clock.now = clock.now.plusSeconds(1)

        then:
        jar.header('/graphql') == null
        jar.snapshot().empty
    }

    void "an Expires date sets the expiry, and Max-Age wins over it"() {
        when:
        jar.store(['a=1; Expires=Tue, 01 Jan 2030 00:10:00 GMT', 'b=2; Expires=Tue, 01 Jan 2030 00:10:00 GMT; Max-Age=30'], '/graphql')

        then:
        jar.snapshot()*.expires() == [Instant.parse('2030-01-01T00:10:00Z'), Instant.parse('2030-01-01T00:00:30Z')]
    }

    void "a malformed attribute is ignored and a nameless cookie is not stored"() {
        when:
        jar.store(['a=1; Max-Age=soon; Expires=never; Path=relative', '=x', 'novalue', 'b=2; Secure; HttpOnly; Domain=other.example'], '/graphql')

        then:
        jar.snapshot() == [new Cookie('a', '1', '/', null), new Cookie('b', '2', '/', null)]
    }

    void "the path rules of RFC 6265 apply"() {
        when:
        jar.store(['a=1; Path=/app', 'b=2; Path=/app/', 'c=3; Path=/'], '/graphql')

        then:
        jar.header(requestPath) == expected

        where:
        requestPath    || expected
        '/app'         || 'a=1; c=3'
        '/app/graphql' || 'b=2; a=1; c=3'
        '/apple'       || 'c=3'
        '/graphql'     || 'c=3'
        ''             || 'c=3'
    }

    void "the default path is the directory of the request path"() {
        when:
        jar.store(['a=1'], requestPath)

        then:
        jar.snapshot()*.path() == [expected]

        where:
        requestPath || expected
        '/graphql'  || '/'
        '/api/gql'  || '/api'
        ''          || '/'
    }

    void "the legacy cookie date formats are understood"() {
        when:
        jar.store(['a=1; Expires=' + date], '/graphql')

        then:
        jar.snapshot()*.expires() == [Instant.parse('2031-06-09T10:18:14Z')]

        where:
        date << ['Mon, 09-Jun-2031 10:18:14 GMT', 'Mon, 09-Jun-31 10:18:14 GMT', 'Monday, 09-Jun-31 10:18:14 GMT']
    }
}
