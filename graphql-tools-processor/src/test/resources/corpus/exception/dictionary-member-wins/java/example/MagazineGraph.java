package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType(name = "Magazine")
public final class MagazineGraph {

    public int getIssue() {
        return 1;
    }

}
