package example.tck

import io.micronaut.graphql.tools.tck.Tck
import spock.lang.Specification

/**
 * The execution-behaviour TCK (#53) in the Groovy suite (JVM only, D-38): every scenario of `graphql-tools-tck` against the
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
