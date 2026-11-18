package example.graphql.query

import groovy.transform.CompileStatic
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@CompileStatic
@GraphQLRootResolver
class DirectivesQuery {

    String shout() {
        'quiet'
    }

    String tagged() {
        'value'
    }
}
