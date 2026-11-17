package io.micronaut.graphql.tools.benchmarks;

class EnumArgumentAllocationTest extends AbstractAllocationGateTest {

    @Override
    String operation() {
        return BenchmarkCases.ENUM_ARGUMENT;
    }
}
