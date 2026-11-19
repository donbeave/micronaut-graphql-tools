package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
@io.micronaut.core.annotation.Introspected(includes = "genre")
public class Book {

    public String getTitle() {
        return null;
    }

    public Genre getGenre() {
        return null;
    }

}
