package io.micronaut.graphql.tools.benchmarks;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Scope;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class FetcherBenchmark extends CaseState {

    private BenchmarkCases.Op propertyFetch;
    private BenchmarkCases.Op graphqlJavaPropertyFetch;
    private BenchmarkCases.Op rootMethod;
    private BenchmarkCases.Op typeResolverMethod;
    private BenchmarkCases.Op typeResolverMethodWithArgument;
    private BenchmarkCases.Op directiveWrapped;
    private BenchmarkCases.Op scalarArgument;
    private BenchmarkCases.Op enumArgument;
    private BenchmarkCases.Op inputObjectArgument;
    private BenchmarkCases.Op listOfInputsArgument;
    private BenchmarkCases.Op listOfScalarsArgument;
    private BenchmarkCases.Op recordInputArgument;
    private BenchmarkCases.Op nestedInputArgument;

    @Setup
    public void prepare() {
        propertyFetch = op(BenchmarkCases.PROPERTY_FETCH);
        graphqlJavaPropertyFetch = op(BenchmarkCases.GRAPHQL_JAVA_PROPERTY_FETCH);
        rootMethod = op(BenchmarkCases.ROOT_METHOD);
        typeResolverMethod = op(BenchmarkCases.TYPE_RESOLVER_METHOD);
        typeResolverMethodWithArgument = op(BenchmarkCases.TYPE_RESOLVER_METHOD_ARGUMENT);
        directiveWrapped = op(BenchmarkCases.DIRECTIVE_WRAPPED_FETCHER);
        scalarArgument = op(BenchmarkCases.SCALAR_ARGUMENT);
        enumArgument = op(BenchmarkCases.ENUM_ARGUMENT);
        inputObjectArgument = op(BenchmarkCases.INPUT_OBJECT_ARGUMENT);
        listOfInputsArgument = op(BenchmarkCases.LIST_OF_INPUTS_ARGUMENT);
        listOfScalarsArgument = op(BenchmarkCases.LIST_OF_SCALARS_ARGUMENT);
        recordInputArgument = op(BenchmarkCases.RECORD_INPUT_ARGUMENT);
        nestedInputArgument = op(BenchmarkCases.NESTED_INPUT_ARGUMENT);
    }

    @Benchmark
    public Object propertyFetch() throws Exception {
        return propertyFetch.run();
    }

    @Benchmark
    public Object graphqlJavaPropertyFetch() throws Exception {
        return graphqlJavaPropertyFetch.run();
    }

    @Benchmark
    public Object rootMethod() throws Exception {
        return rootMethod.run();
    }

    @Benchmark
    public Object typeResolverMethod() throws Exception {
        return typeResolverMethod.run();
    }

    @Benchmark
    public Object typeResolverMethodWithArgument() throws Exception {
        return typeResolverMethodWithArgument.run();
    }

    @Benchmark
    public Object directiveWrappedFetcher() throws Exception {
        return directiveWrapped.run();
    }

    @Benchmark
    public Object scalarArgument() throws Exception {
        return scalarArgument.run();
    }

    @Benchmark
    public Object enumArgument() throws Exception {
        return enumArgument.run();
    }

    @Benchmark
    public Object inputObjectArgument() throws Exception {
        return inputObjectArgument.run();
    }

    @Benchmark
    public Object listOfInputsArgument() throws Exception {
        return listOfInputsArgument.run();
    }

    @Benchmark
    public Object listOfScalarsArgument() throws Exception {
        return listOfScalarsArgument.run();
    }

    @Benchmark
    public Object recordInputArgument() throws Exception {
        return recordInputArgument.run();
    }

    @Benchmark
    public Object nestedInputArgument() throws Exception {
        return nestedInputArgument.run();
    }
}
