package io.micronaut.graphql.tools.benchmarks;

class Query1kObjectsAllocationTest extends AbstractAllocationGateTest {

    @Override
    String operation() {
        return BenchmarkCases.QUERY_1K;
    }
}
