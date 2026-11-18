package io.micronaut.graphql.tools.benchmarks;

class RecordInputArgumentAllocationTest extends AbstractAllocationGateTest {

    @Override
    String operation() {
        return BenchmarkCases.RECORD_INPUT_ARGUMENT;
    }
}
