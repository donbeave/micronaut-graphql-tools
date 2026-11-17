package io.micronaut.graphql.tools.benchmarks;

class InputObjectArgumentAllocationTest extends AbstractAllocationGateTest {

    @Override
    String operation() {
        return BenchmarkCases.INPUT_OBJECT_ARGUMENT;
    }
}
