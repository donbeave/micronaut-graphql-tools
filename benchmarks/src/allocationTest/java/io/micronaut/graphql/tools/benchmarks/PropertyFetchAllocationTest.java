package io.micronaut.graphql.tools.benchmarks;

class PropertyFetchAllocationTest extends AbstractAllocationGateTest {

    @Override
    String operation() {
        return BenchmarkCases.PROPERTY_FETCH;
    }
}
