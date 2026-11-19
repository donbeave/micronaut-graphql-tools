package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class Book extends Titled<String> {

    public Genre getGenre() {
        return null;
    }

}
