package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver

@Singleton
@GraphQLTypeResolver(Section)
class SectionResolver {

    @GraphQLSchemaCoordinate("Section.summary")
    String summary(Section section) {
        return 'resolved ' + section.title
    }

}
