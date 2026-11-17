package io.micronaut.graphql.tools.benchmarks;

class ScalarArgumentAllocationTest extends AbstractAllocationGateTest {

    @Override
    String operation() {
        return BenchmarkCases.SCALAR_ARGUMENT;
    }
}
