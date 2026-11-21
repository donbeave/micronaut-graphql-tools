package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class BookModel implements SearchResult {

    public String getTitle() {
        return "title";
    }

}
