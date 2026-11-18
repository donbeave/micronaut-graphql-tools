package example

import io.micronaut.graphql.tools.annotation.GraphQLArgument

interface Contract {

    String size(@GraphQLArgument('short') Integer width, @GraphQLArgument('long') Integer length)

}
