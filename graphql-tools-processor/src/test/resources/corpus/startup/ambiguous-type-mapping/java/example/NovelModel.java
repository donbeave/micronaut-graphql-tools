package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class NovelModel implements BookGraph {

    @Override
    public String getTitle() {
        return "title";
    }

}
