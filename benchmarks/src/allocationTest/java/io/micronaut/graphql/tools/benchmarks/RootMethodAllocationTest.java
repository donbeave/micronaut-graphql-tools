package io.micronaut.graphql.tools.benchmarks;

class RootMethodAllocationTest extends AbstractAllocationGateTest {

    @Override
    String operation() {
        return BenchmarkCases.ROOT_METHOD;
    }
}
