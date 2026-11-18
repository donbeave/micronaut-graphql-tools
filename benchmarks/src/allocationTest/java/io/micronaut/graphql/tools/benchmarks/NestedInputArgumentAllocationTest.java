package io.micronaut.graphql.tools.benchmarks;

class NestedInputArgumentAllocationTest extends AbstractAllocationGateTest {

    @Override
    String operation() {
        return BenchmarkCases.NESTED_INPUT_ARGUMENT;
    }
}
