package io.micronaut.graphql.tools.mapping.resolver.type

import graphql.schema.DataFetchingEnvironment
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import org.intellij.lang.annotations.Language

import static java.util.Arrays.asList

class TypeResolverDataFetchingEnvironmentSpec extends AbstractTest {

    static final String SPEC_NAME = "TypeResolverDataFetchingEnvironmentSpec"

    @Language("GraphQL")
    static String SCHEMA = """
schema {
  query: Query
}

type Query {
  user: User
}

type User {
  username: String!
  paymentMethodList: [PaymentMethod!]!
}

type PaymentMethod {
  number: String!
}
"""

    void "DataFetchingEnvironment passed to a GraphQLTypeResolver's method"() {
        given:
            startContext(SCHEMA, SPEC_NAME)

        when:
            def result = executeQuery("""
{ 
    user {
        username
        paymentMethodList {
            number
        }
    }
}
""")

        then:
            result.errors.isEmpty()
            result.dataPresent
            result.data.user.username == 'test'
            result.data.user.paymentMethodList.size() == 2
            result.data.user.paymentMethodList[0].number == '123'
            result.data.user.paymentMethodList[1].number == '456'
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        User user() {
            return new User(username: 'test')
        }
    }

    @GraphQLType
    static class User {
        String username
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLTypeResolver(User.class)
    static class UserResolver {
        List<PaymentMethod> paymentMethodList(User user, DataFetchingEnvironment env) {
            assert env != null
            assert env.field.name == 'paymentMethodList'
            assert env.parentType.name == 'User'
            return asList(
                    new PaymentMethod(number: '123'),
                    new PaymentMethod(number: '456')
            )
        }
    }

    @GraphQLType
    static class PaymentMethod {
        String number
    }

}
