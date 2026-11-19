package example;

import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;

@GraphQLTypeResolver(Section.class)
public class SectionResolver {

    @GraphQLSchemaCoordinate("Section.summary")
    public String summary(Section section) {
        return "resolved " + section.getTitle();
    }

}
