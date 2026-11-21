package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType(name = "MediaSection")
public final class MediaSection {

    public String getTitle() {
        return "title";
    }

}
