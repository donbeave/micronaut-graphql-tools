package io.micronaut.graphql.tools.benchmarks;

class GraphqlJavaPropertyFetchAllocationTest extends AbstractAllocationGateTest {

    @Override
    String operation() {
        return BenchmarkCases.GRAPHQL_JAVA_PROPERTY_FETCH;
    }
}
