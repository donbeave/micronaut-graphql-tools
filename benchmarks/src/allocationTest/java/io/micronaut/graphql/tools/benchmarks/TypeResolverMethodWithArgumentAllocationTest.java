package io.micronaut.graphql.tools.benchmarks;

class TypeResolverMethodWithArgumentAllocationTest extends AbstractAllocationGateTest {

    @Override
    String operation() {
        return BenchmarkCases.TYPE_RESOLVER_METHOD_ARGUMENT;
    }
}
