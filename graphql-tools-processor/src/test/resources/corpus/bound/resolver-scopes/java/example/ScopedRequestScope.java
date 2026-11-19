package example;

@io.micronaut.runtime.http.scope.RequestScope
@io.micronaut.graphql.tools.annotation.GraphQLRootResolver
public class ScopedRequestScope {

    public String fieldRequestScope() {
        return null;
    }

}
