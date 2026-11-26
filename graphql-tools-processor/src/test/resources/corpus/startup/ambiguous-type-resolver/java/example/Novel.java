package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class Novel {

    public String getTitle() {
        return "T";
    }

    public Writer getAuthor() {
        return new Writer();
    }

}
