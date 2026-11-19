package example

import io.micronaut.graphql.tools.annotation.GraphQLType
import java.util.Optional

@GraphQLType
class Book(val title: Optional<String>)
