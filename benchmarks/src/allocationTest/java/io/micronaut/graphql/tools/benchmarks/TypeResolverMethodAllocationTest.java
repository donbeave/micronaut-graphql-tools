package io.micronaut.graphql.tools.benchmarks;

class TypeResolverMethodAllocationTest extends AbstractAllocationGateTest {

    @Override
    String operation() {
        return BenchmarkCases.TYPE_RESOLVER_METHOD;
    }
}
