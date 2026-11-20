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
package io.micronaut.graphql.tools.websocket

import spock.lang.Specification

class GraphQLConnectionResultSpec extends Specification {

    def "a reason at the close frame limit is accepted"() {
        expect:
        GraphQLConnectionResult.reject(4401, "a" * 123).reason().length() == 123
    }

    def "a reason over the close frame limit in UTF-8 bytes is rejected"() {
        when:
        GraphQLConnectionResult.reject(4401, reason)

        then:
        thrown(IllegalArgumentException)

        where:
        reason << ["a" * 124, "é" * 62]
    }
}
