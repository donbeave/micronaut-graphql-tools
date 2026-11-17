package io.micronaut.graphql.tools.benchmarks;

class UnionResolutionAllocationTest extends AbstractAllocationGateTest {

    @Override
    String operation() {
        return BenchmarkCases.UNION_RESOLUTION;
    }
}
