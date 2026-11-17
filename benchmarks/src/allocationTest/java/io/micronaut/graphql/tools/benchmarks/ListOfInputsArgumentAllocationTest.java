package io.micronaut.graphql.tools.benchmarks;

class ListOfInputsArgumentAllocationTest extends AbstractAllocationGateTest {

    @Override
    String operation() {
        return BenchmarkCases.LIST_OF_INPUTS_ARGUMENT;
    }
}
