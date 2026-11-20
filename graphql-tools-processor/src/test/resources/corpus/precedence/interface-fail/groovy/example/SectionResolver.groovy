package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver

@Singleton
@GraphQLTypeResolver(Section)
class SectionResolver {

    String summary(Section section) {
        return 'resolved ' + section.title
    }

}
