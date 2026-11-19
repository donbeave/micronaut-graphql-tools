package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class User implements Node {

    public Long getId() {
        return 7L;
    }

    public String getName() {
        return "ann";
    }

}
