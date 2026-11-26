package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class Writer {

    public String getName() {
        return "N";
    }

}
