package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class MediaSection implements Section {

    @Override
    public String getTitle() {
        return "media";
    }

}
