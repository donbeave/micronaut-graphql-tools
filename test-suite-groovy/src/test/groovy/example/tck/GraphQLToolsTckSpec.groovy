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
package example.tck

import io.micronaut.graphql.tools.tck.Tck
import spock.lang.Specification

/**
 * The execution-behaviour TCK (#53) in the Groovy suite (JVM only): every scenario of `graphql-tools-tck` against the
 * fixtures in this package, with raw HTTP requests to the running application. The same scenarios run in the Java and Kotlin
 * suites.
 */
class GraphQLToolsTckSpec extends Specification {

    def "scenario #id"() {
        expect: 'the failure message of the scenario lists every case that differs'
        Tck.run(id)

        where:
        id << Tck.scenarioIds()
    }

    def "exemptions are current"() {
        expect:
        Tck.checkExemptions()
    }
}
