package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class MagazineRecord implements SearchResult {

    public int getIssue() {
        return 1;
    }

}
