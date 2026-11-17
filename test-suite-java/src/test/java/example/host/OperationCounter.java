package example.host;

import graphql.ExecutionResult;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The application's own instrumentation: counts the operations its {@code GraphQL} bean executes.
 */
@Singleton
@Requires(env = HostGraphQLFactory.ENVIRONMENT)
public class OperationCounter extends SimplePerformantInstrumentation {

    private final AtomicInteger operations = new AtomicInteger();

    @Override
    public InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters,
                                                                  InstrumentationState state) {
        operations.incrementAndGet();
        return super.beginExecution(parameters, state);
    }

    public int getOperations() {
        return operations.get();
    }

}
