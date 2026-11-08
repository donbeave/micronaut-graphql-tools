package io.micronaut.graphql.tools.mapping.interfacetype

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.SchemaMappingDictionaryCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language

/**
 * Union members that implement an interface no field returns: the interface is bound through its reachable
 * implementations. Unions whose members implement no interface (and share a member) keep working.
 */
class InterfaceImplementedOnlySpec extends AbstractTest {

    static final String SPEC_NAME = "InterfaceImplementedOnlySpec"

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  authorError(access: Boolean!): AuthorError
}

union AuthorError = ServerError | AccessError

interface Error {
  message: String!
}

type ServerError implements Error {
  code: ServerErrorCode!
  message: String!
}

type AccessError implements Error {
  code: AccessErrorCode!
  message: String!
}

enum ServerErrorCode { INTERNAL }
enum AccessErrorCode { DENIED }
"""

    void "an interface that only union members implement is bound, and the union resolves"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = executeQuery("""
{
    access: authorError(access: true) {
        __typename
        ... on Error { message }
        ... on AccessError { accessCode: code }
    }
    server: authorError(access: false) {
        __typename
        ... on Error { message }
        ... on ServerError { serverCode: code }
    }
    __type(name: "Error") {
        kind
        possibleTypes { name }
    }
}
""")

        then:
        result.errors.isEmpty()
        result.data.access == [__typename: 'AccessError', message: 'login required', accessCode: 'DENIED']
        result.data.server == [__typename: 'ServerError', message: 'boom', serverCode: 'INTERNAL']
        result.data.__type.kind == 'INTERFACE'
        result.data.__type.possibleTypes*.name.toSorted() == ['AccessError', 'ServerError']
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        AuthorError authorError(boolean access) {
            return access ? new AccessError(message: 'login required') : new ServerError(message: 'boom')
        }
    }

    static interface AuthorError {
    }

    @GraphQLType
    static class ServerError implements AuthorError, ShelfError {
        ServerErrorCode code = ServerErrorCode.INTERNAL
        String message
    }

    @GraphQLType
    static class AccessError implements AuthorError {
        AccessErrorCode code = AccessErrorCode.DENIED
        String message
    }

    static enum ServerErrorCode {
        INTERNAL
    }

    static enum AccessErrorCode {
        DENIED
    }

    @Requires(property = 'spec.name', pattern = SPEC_NAME + '.*')
    @Factory
    static class DictionaryFactory {
        @Bean
        @Singleton
        SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
            return (schemaMappingDictionary) -> schemaMappingDictionary
                    .registerType("ServerError", ServerError.class)
                    .registerType("AccessError", AccessError.class)
        }
    }

    // --- unions without interfaces, sharing a member, one of them nested in a payload type ---

    static final String UNIONS_SPEC_NAME = SPEC_NAME + ".unions"

    @Language("GraphQL")
    static final String UNIONS_SCHEMA = """
schema {
  query: Query
}

type Query {
  shelfPayload: ShelfPayload
  authorError(access: Boolean!): AuthorError
}

type ShelfPayload {
  error: ShelfError
}

union ShelfError = ServerError
union AuthorError = ServerError | AccessError

type ServerError {
  code: ServerErrorCode!
  message: String!
}

type AccessError {
  code: AccessErrorCode!
  message: String!
}

enum ServerErrorCode { INTERNAL }
enum AccessErrorCode { DENIED }
"""

    void "unions whose members implement no interface keep working, also when they share a member"() {
        given:
        startContext(UNIONS_SCHEMA, UNIONS_SPEC_NAME)

        when:
        def result = executeQuery("""
{
    shelfPayload {
        error {
            ... on ServerError { serverCode: code message }
        }
    }
    access: authorError(access: true) {
        ... on AccessError { accessCode: code message }
        ... on ServerError { serverCode: code message }
    }
    server: authorError(access: false) {
        ... on AccessError { accessCode: code message }
        ... on ServerError { serverCode: code message }
    }
}
""")

        then:
        result.errors.isEmpty()
        result.data.shelfPayload == [error: [serverCode: 'INTERNAL', message: 'boom']]
        result.data.access == [accessCode: 'DENIED', message: 'login required']
        result.data.server == [serverCode: 'INTERNAL', message: 'boom']
    }

    void "an object of a class that no union member is mapped to is a GraphQL error naming the union, the path and the class"() {
        given:
        startContext(UNIONS_SCHEMA, UNIONS_SPEC_NAME + ".unknown")

        when:
        def result = executeQuery("""
{
    shelfPayload {
        error {
            ... on ServerError { message }
        }
    }
}
""")

        then:
        result.errors.size() == 1
        result.errors[0].path == ['shelfPayload', 'error']
        result.errors[0].message == "Can't resolve '/shelfPayload/error'. Abstract type 'ShelfError' must resolve " +
                "to an Object type at runtime for field 'ShelfPayload.error'. The object of class " +
                "${UnknownError.name} is none of the object types of union ShelfError: ServerError " +
                "(${ServerError.name})."
        result.data.shelfPayload == [error: null]
    }

    @Requires(property = 'spec.name', value = UNIONS_SPEC_NAME)
    @GraphQLRootResolver
    static class UnionsQuery {
        ShelfPayload shelfPayload() {
            return new ShelfPayload(error: new ServerError(message: 'boom'))
        }

        AuthorError authorError(boolean access) {
            return access ? new AccessError(message: 'login required') : new ServerError(message: 'boom')
        }
    }

    @GraphQLType
    static class ShelfPayload {
        ShelfError error
    }

    static interface ShelfError {
    }

    static class UnknownError implements ShelfError {
    }

    @Requires(property = 'spec.name', value = UNIONS_SPEC_NAME + ".unknown")
    @GraphQLRootResolver
    static class UnknownErrorQuery {
        ShelfPayload shelfPayload() {
            return new ShelfPayload(error: new UnknownError())
        }

        AuthorError authorError(boolean access) {
            return null
        }
    }

}
