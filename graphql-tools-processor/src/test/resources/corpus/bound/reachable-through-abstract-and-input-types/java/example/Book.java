package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class Book implements Result {

    public String getTitle() {
        return null;
    }

}
