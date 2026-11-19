package example;

@io.micronaut.context.annotation.Bean
@io.micronaut.graphql.tools.annotation.GraphQLRootResolver
public class ScopedBean {

    public String fieldBean() {
        return null;
    }

}
