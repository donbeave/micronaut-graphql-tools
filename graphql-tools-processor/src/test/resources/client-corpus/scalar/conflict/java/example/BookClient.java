package example;

import io.micronaut.graphql.tools.client.annotation.GraphQLClient;
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation;

@GraphQLClient(schema = "books")
public interface BookClient {

    @GraphQLOperation("query At($moment: DateTime) { at(moment: $moment) }")
    Object at(java.time.Instant moment);

    @GraphQLOperation(value = "query At2($moment: DateTime) { at(moment: $moment) }", name = "At2")
    Object atAgain(String moment);

}
