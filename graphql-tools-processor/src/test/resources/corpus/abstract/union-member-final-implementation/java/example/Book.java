package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType(name = "Book")
public final class Book implements SearchResult {

    public String getTitle() {
        return "title";
    }

}
