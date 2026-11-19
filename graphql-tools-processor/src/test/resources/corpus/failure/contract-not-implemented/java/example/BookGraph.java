package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public interface BookGraph {

    String getTitle();

    Genre getGenre();

}
