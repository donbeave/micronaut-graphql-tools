package io.micronaut.graphql.tools.benchmarks;

class InterfaceResolutionAllocationTest extends AbstractAllocationGateTest {

    @Override
    String operation() {
        return BenchmarkCases.INTERFACE_RESOLUTION;
    }
}
