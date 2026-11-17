package example.tck

import io.micronaut.graphql.tools.tck.Tck
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.stream.Stream

/**
 * The execution-behaviour TCK (#53) in the Kotlin suite: every scenario of `graphql-tools-tck` against the fixtures in this
 * package, with raw HTTP requests to the running application. The same scenarios run in the Java and Groovy suites.
 */
class GraphQLToolsTckTest {

    @TestFactory
    fun scenarios(): Stream<DynamicTest> = Tck.scenarioIds().stream().map { id -> DynamicTest.dynamicTest(id) { Tck.run(id) } }

    @Test
    fun exemptionsAreCurrent() {
        Tck.checkExemptions()
    }
}
