package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;

@Singleton
@GraphQLTypeResolver(Section.class)
public class SectionResolver {

    public String summary(Section section) {
        return "resolved " + section.getTitle();
    }

}
