package example;

import graphql.ExecutionResult;
import io.micronaut.configuration.graphql.DefaultGraphQLInvocation;
import io.micronaut.configuration.graphql.GraphQLInvocation;
import io.micronaut.configuration.graphql.GraphQLInvocationData;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

@Singleton
@Replaces(DefaultGraphQLInvocation.class)
public class OwnInvocation implements GraphQLInvocation {

    @Override
    public Publisher<ExecutionResult> invoke(GraphQLInvocationData invocationData, HttpRequest httpRequest,
                                             MutableHttpResponse<String> httpResponse) {
        return Mono.empty();
    }

}
