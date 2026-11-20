package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class Book implements SearchResult {

    public String getTitle() {
        return "title";
    }

}
