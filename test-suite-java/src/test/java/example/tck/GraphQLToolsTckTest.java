package example.tck;

import io.micronaut.graphql.tools.tck.Tck;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

/**
 * The execution-behaviour TCK (#53) in the Java suite: every scenario of {@code graphql-tools-tck} against the fixtures in
 * this package, with raw HTTP requests to the running application. The same scenarios run in the Kotlin and Groovy suites.
 */
class GraphQLToolsTckTest {

    @TestFactory
    Stream<DynamicTest> scenarios() {
        return Tck.scenarioIds().stream().map(id -> DynamicTest.dynamicTest(id, () -> Tck.run(id)));
    }

    @Test
    void exemptionsAreCurrent() {
        Tck.checkExemptions();
    }

}
