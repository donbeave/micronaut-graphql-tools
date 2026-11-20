package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class Book {

    public String getTitle() {
        return "t";
    }

}
