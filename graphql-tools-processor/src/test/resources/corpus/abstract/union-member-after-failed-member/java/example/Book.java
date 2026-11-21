package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType(name = "Book")
public class Book implements SearchResult {

    public Integer getTitle() {
        return 1;
    }

}
