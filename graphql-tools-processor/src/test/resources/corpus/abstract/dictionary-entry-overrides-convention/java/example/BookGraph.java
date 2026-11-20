package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class BookGraph implements SearchResult {

    public String getTitle() {
        return "graph";
    }

}
