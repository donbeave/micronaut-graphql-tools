package example

import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver

@GraphQLTypeResolver(Section)
class SectionResolver {

    @GraphQLSchemaCoordinate("Section.summary")
    String summary(Section section) {
        return 'resolved ' + section.title
    }

}
