package example

import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver

@GraphQLTypeResolver(Section)
class SectionResolver {

    String summary(Section section) {
        return 'resolved ' + section.title
    }

}
