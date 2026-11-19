package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.List;

@GraphQLRootResolver
public class HelloQuery {

    public String hello() {
        return "world";
    }

    public List<Result> search(Filter filter) {
        return null;
    }

    public Node node() {
        return null;
    }

}
