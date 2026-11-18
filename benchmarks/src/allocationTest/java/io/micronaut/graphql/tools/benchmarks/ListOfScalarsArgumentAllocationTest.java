package io.micronaut.graphql.tools.benchmarks;

class ListOfScalarsArgumentAllocationTest extends AbstractAllocationGateTest {

    @Override
    String operation() {
        return BenchmarkCases.LIST_OF_SCALARS_ARGUMENT;
    }
}
