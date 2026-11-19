package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class Book extends Titled<Integer> {

    public Genre getGenre() {
        return null;
    }

}
