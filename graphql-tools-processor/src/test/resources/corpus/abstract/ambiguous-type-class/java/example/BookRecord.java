package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType(name = "Book")
public class BookRecord implements SearchResult {

    public String getTitle() {
        return "title";
    }

}
