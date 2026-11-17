package io.micronaut.graphql.tools.benchmarks;

class DirectiveWrappedFetcherAllocationTest extends AbstractAllocationGateTest {

    @Override
    String operation() {
        return BenchmarkCases.DIRECTIVE_WRAPPED_FETCHER;
    }
}
