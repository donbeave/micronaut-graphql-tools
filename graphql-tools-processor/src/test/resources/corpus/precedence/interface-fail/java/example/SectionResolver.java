package example;

import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;

@GraphQLTypeResolver(Section.class)
public class SectionResolver {

    public String summary(Section section) {
        return "resolved " + section.getTitle();
    }

}
