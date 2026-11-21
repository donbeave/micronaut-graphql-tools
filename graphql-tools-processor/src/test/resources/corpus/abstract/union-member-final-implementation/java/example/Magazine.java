package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType(name = "Magazine")
public final class Magazine implements SearchResult {

    public int getIssue() {
        return 1;
    }

}
