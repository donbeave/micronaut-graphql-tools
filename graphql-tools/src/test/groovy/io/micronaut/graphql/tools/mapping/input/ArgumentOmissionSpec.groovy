package io.micronaut.graphql.tools.mapping.input

import graphql.ExecutionInput
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.ArgumentValue
import io.micronaut.graphql.tools.GraphQLToolsConfiguration
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.exceptions.UnsupportedInputTypeException
import org.intellij.lang.annotations.Language

/**
 * Omitted arguments and input fields versus explicit {@code null}: {@code micronaut.graphql.tools.input.detect-omission}
 * (default {@code false}) enables {@link ArgumentValue}. In both modes the schema's default values apply and enum
 * values arrive as the Java enum constants of the same name.
 */
class ArgumentOmissionSpec extends AbstractTest {

    static final String SPEC_DEFAULT = "ArgumentOmissionSpecDefault"
    static final String SPEC_WRAPPER_WITHOUT_OPTION = "ArgumentOmissionSpecWrapperWithoutOption"
    static final String SPEC_WRAPPER_IN_INPUT_WITHOUT_OPTION = "ArgumentOmissionSpecWrapperInInputWithoutOption"
    static final String SPEC_DETECT = "ArgumentOmissionSpecDetect"
    static final String SPEC_NON_NULL = "ArgumentOmissionSpecNonNull"
    static final String SPEC_LIST_ELEMENT = "ArgumentOmissionSpecListElement"
    static final String SPEC_READ_ONLY = "ArgumentOmissionSpecReadOnly"
    static final String SPEC_WRITE_ONLY = "ArgumentOmissionSpecWriteOnly"
    static final String SPEC_WRITE_ONLY_WRAPPER = "ArgumentOmissionSpecWriteOnlyWrapper"
    static final String SPEC_WRITE_ONLY_NESTED = "ArgumentOmissionSpecWriteOnlyNested"

    static final Map DETECT = [(GraphQLToolsConfiguration.PREFIX + '.input.detect-omission'): true]

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  update(name: String, color: Color, limit: Int = 10, profile: ProfileInput): String!
}

enum Color {
  RED
  GREEN
}

input ProfileInput {
  nickname: String
  color: Color = GREEN
  tags: [String]
  address: AddressInput
}

input AddressInput {
  city: String
  zip: String
}
"""

    void "default: an omitted argument and an explicit null both arrive as null; SDL defaults and enums apply"() {
        given:
        startContext(SCHEMA, SPEC_DEFAULT)

        expect:
        run('{ update }') ==
                'name=null color=null limit=10 profile=null'
        run('{ update(name: null, color: null, limit: null, profile: null) }') ==
                'name=null color=null limit=null profile=null'
        run('{ update(name: "Jane", color: RED, limit: 3) }') ==
                'name=Jane color=RED(Color) limit=3 profile=null'
        run('{ update(profile: {nickname: "jj"}) }') ==
                'name=null color=null limit=10 profile={nickname=jj color=GREEN(Color) tags=[unset] address=null}'
        run('{ update(profile: {nickname: null, tags: null, color: RED}) }') ==
                'name=null color=null limit=10 profile={nickname=null color=RED(Color) tags=null address=null}'
    }

    void "default: an ArgumentValue parameter fails the startup and names the option"() {
        given:
        startContext(SCHEMA, SPEC_WRAPPER_WITHOUT_OPTION)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof UnsupportedInputTypeException
        e.cause.message.startsWith("The Java type is not supported for this argument or input field: " +
                "${ArgumentValue.name} needs micronaut.graphql.tools.input.detect-omission=true.\n" +
                "  GraphQL coordinate: Query.update(name:) (line 7, column 10)\n" +
                "  GraphQL object type: Query\n  GraphQL field: update\n  GraphQL argument: name")
    }

    void "default: an ArgumentValue property of an input class fails the startup and names the option"() {
        given:
        startContext(SCHEMA, SPEC_WRAPPER_IN_INPUT_WITHOUT_OPTION)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof UnsupportedInputTypeException
        e.cause.message == """The Java type is not supported for this argument or input field: ${ArgumentValue.name} needs micronaut.graphql.tools.input.detect-omission=true.
  GraphQL coordinate: ProfileInput.nickname (line 16, column 3)
  GraphQL input object type: ProfileInput
  GraphQL input value: nickname
  Mapped class: ${WrappedProfileInput.name}
  Mapped property: nickname"""
    }

    void "detect-omission: ArgumentValue arguments tell omitted, null and value apart; SDL defaults and enums apply"() {
        given:
        startContext(SCHEMA, SPEC_DETECT, DETECT)

        expect:
        run('{ update }') ==
                'name=omitted color=omitted limit=value(10) profile=omitted'
        run('{ update(name: null, color: null, limit: null, profile: null) }') ==
                'name=null color=null limit=null profile=null'
        run('{ update(name: "Jane", color: RED, limit: 3) }') ==
                'name=value(Jane) color=value(RED Color) limit=value(3) profile=omitted'
    }

    void "detect-omission: ArgumentValue properties of input objects, also nested ones"() {
        given:
        startContext(SCHEMA, SPEC_DETECT, DETECT)

        expect:
        run('{ update(profile: {}) }') ==
                'name=omitted color=omitted limit=value(10) ' +
                'profile=value({nickname=omitted color=value(GREEN Color) tags=[unset] address=omitted})'
        run('{ update(profile: {nickname: null, color: RED, tags: null, address: null}) }') ==
                'name=omitted color=omitted limit=value(10) ' +
                'profile=value({nickname=null color=value(RED Color) tags=null address=null})'
        run('{ update(profile: {nickname: "jj", tags: ["a"], address: {zip: "10001"}}) }') ==
                'name=omitted color=omitted limit=value(10) ' +
                'profile=value({nickname=value(jj) color=value(GREEN Color) tags=[a] address=value({city=omitted zip=10001})})'
        run('{ update(profile: {address: {city: null}}) }') ==
                'name=omitted color=omitted limit=value(10) ' +
                'profile=value({nickname=omitted color=value(GREEN Color) tags=[unset] address=value({city=null zip=null})})'
    }

    void "detect-omission: a variable that is not provided is omitted, a variable set to null is null"() {
        given:
        startContext(SCHEMA, SPEC_DETECT, DETECT)
        String query = 'query ($n: String, $p: ProfileInput) { update(name: $n, profile: $p) }'

        expect:
        execute(query, [:]) ==
                'name=omitted color=omitted limit=value(10) profile=omitted'
        execute(query, [n: null, p: [nickname: null]]) ==
                'name=null color=omitted limit=value(10) ' +
                'profile=value({nickname=null color=value(GREEN Color) tags=[unset] address=omitted})'
        execute(query, [n: 'Jane', p: [:]]) ==
                'name=value(Jane) color=omitted limit=value(10) ' +
                'profile=value({nickname=omitted color=value(GREEN Color) tags=[unset] address=omitted})'
    }

    void "detect-omission: ArgumentValue on a non-null type fails the startup"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  rename(name: String!): String
}
"""
        startContext(schema, SPEC_NON_NULL, DETECT)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof UnsupportedInputTypeException
        e.cause.reason == "${ArgumentValue.name} is only allowed for a nullable GraphQL type, the type is String!."
    }

    void "detect-omission: ArgumentValue as a list element fails the startup"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  tag(tags: [String]): String
}
"""
        startContext(schema, SPEC_LIST_ELEMENT, DETECT)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof UnsupportedInputTypeException
        e.cause.reason == "${ArgumentValue.name} is not allowed inside a list: a list element cannot be omitted."
    }

    void "detect-omission: a read-only ArgumentValue property fails the startup"() {
        given:
        startContext(SCHEMA, SPEC_READ_ONLY, DETECT)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof UnsupportedInputTypeException
        e.cause.message == """The Java type is not supported for this argument or input field: ${ArgumentValue.name} needs a writable property: it is set to omitted() when the client leaves it out.
  GraphQL coordinate: ProfileInput.nickname (line 16, column 3)
  GraphQL input object type: ProfileInput
  GraphQL input value: nickname
  Mapped class: ${ReadOnlyProfileInput.name}
  Mapped property: nickname"""
    }

    void "detect-omission: write-only properties and computed getters of input classes are never read"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  login(input: LoginInput): String
}

input LoginInput {
  user: String
  password: String
}
"""
        startContext(schema, SPEC_WRITE_ONLY, DETECT)

        when:
        def result = executeQuery('{ login(input: {password: "secret"}) }')

        then:
        result.errors.isEmpty()
        result.data.login == 'user=omitted password stored=true'
    }

    void "detect-omission: a write-only ArgumentValue property fails the startup"() {
        given:
        startContext(SCHEMA, SPEC_WRITE_ONLY_WRAPPER, DETECT)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof UnsupportedInputTypeException
        e.cause.reason == "${ArgumentValue.name} needs a readable property: it is read to set it to omitted() when " +
                "the client leaves it out."
        e.cause.mappingContext.graphQlInputValue == 'nickname'
    }

    void "detect-omission: a write-only property whose input class has ArgumentValue properties fails the startup"() {
        given:
        startContext(SCHEMA, SPEC_WRITE_ONLY_NESTED, DETECT)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof UnsupportedInputTypeException
        e.cause.reason == "the property is write-only, but its input class ${DetectAddressInput.name} has " +
                "${ArgumentValue.name} properties, which are read to set them to omitted() when the client leaves " +
                "them out."
        e.cause.mappingContext.graphQlInputValue == 'address'
    }

    void "ArgumentValue states"() {
        expect:
        ArgumentValue.omitted().isOmitted()
        !ArgumentValue.omitted().isNull()
        !ArgumentValue.omitted().isPresent()
        ArgumentValue.ofNullable(null).isNull()
        !ArgumentValue.ofNullable(null).isOmitted()
        ArgumentValue.ofNullable('a').isPresent()
        ArgumentValue.ofNullable('a').get() == 'a'
        ArgumentValue.ofNullable(null).orElse('b') == 'b'
        ArgumentValue.ofNullable('a') == ArgumentValue.ofNullable('a')
        ArgumentValue.omitted() != ArgumentValue.ofNullable(null)

        when:
        ArgumentValue.omitted().get()

        then:
        thrown(NoSuchElementException)
    }

    private String run(String query) {
        def result = executeQuery(query)
        assert result.errors.isEmpty()
        return result.data.update
    }

    private String execute(String query, Map variables) {
        def result = graphQLBean.execute(ExecutionInput.newExecutionInput().query(query).variables(variables).build())
        assert result.errors.isEmpty()
        return result.data.update
    }

    static String show(Object value) {
        if (value instanceof ArgumentValue) {
            ArgumentValue argumentValue = (ArgumentValue) value
            if (argumentValue.omitted) {
                return 'omitted'
            }
            if (argumentValue.null) {
                return 'null'
            }
            def inner = argumentValue.value
            return inner instanceof Enum ? "value(${inner} ${inner.class.simpleName})" : "value(${show(inner)})"
        }
        if (value instanceof DetectProfileInput) {
            return "{nickname=${show(value.nickname)} color=${show(value.color)} tags=${value.tags} " +
                    "address=${show(value.address)}}"
        }
        if (value instanceof DetectAddressInput) {
            return "{city=${show(value.city)} zip=${value.zip}}"
        }
        if (value instanceof ProfileInput) {
            return "{nickname=${value.nickname} color=${show(value.color)} tags=${value.tags} address=${value.address}}"
        }
        if (value instanceof Enum) {
            return "${value}(${value.class.simpleName})"
        }
        return String.valueOf(value)
    }

    @Requires(property = 'spec.name', value = SPEC_DEFAULT)
    @GraphQLRootResolver
    static class DefaultQuery {
        String update(String name, Color color, Integer limit, ProfileInput profile) {
            return "name=${name} color=${show(color)} limit=${limit} profile=${show(profile)}"
        }
    }

    @Requires(property = 'spec.name', value = SPEC_WRAPPER_WITHOUT_OPTION)
    @GraphQLRootResolver
    static class WrapperWithoutOptionQuery {
        String update(ArgumentValue<String> name, Color color, Integer limit, ProfileInput profile) {
            return null
        }
    }

    @Requires(property = 'spec.name', value = SPEC_WRAPPER_IN_INPUT_WITHOUT_OPTION)
    @GraphQLRootResolver
    static class WrapperInInputWithoutOptionQuery {
        String update(String name, Color color, Integer limit, WrappedProfileInput profile) {
            return null
        }
    }

    @Requires(property = 'spec.name', value = SPEC_DETECT)
    @GraphQLRootResolver
    static class DetectQuery {
        String update(ArgumentValue<String> name, ArgumentValue<Color> color, ArgumentValue<Integer> limit,
                      ArgumentValue<DetectProfileInput> profile) {
            return "name=${show(name)} color=${show(color)} limit=${show(limit)} profile=${show(profile)}"
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NON_NULL)
    @GraphQLRootResolver
    static class NonNullQuery {
        String rename(ArgumentValue<String> name) {
            return null
        }
    }

    @Requires(property = 'spec.name', value = SPEC_LIST_ELEMENT)
    @GraphQLRootResolver
    static class ListElementQuery {
        String tag(List<ArgumentValue<String>> tags) {
            return null
        }
    }

    @Requires(property = 'spec.name', value = SPEC_READ_ONLY)
    @GraphQLRootResolver
    static class ReadOnlyQuery {
        String update(String name, Color color, Integer limit, ReadOnlyProfileInput profile) {
            return null
        }
    }

    @GraphQLInput
    static class ReadOnlyProfileInput {
        Color color
        List<String> tags
        AddressInput address

        ArgumentValue<String> getNickname() {
            return ArgumentValue.omitted()
        }
    }

    @Requires(property = 'spec.name', value = SPEC_WRITE_ONLY)
    @GraphQLRootResolver
    static class WriteOnlyQuery {
        String login(LoginInput input) {
            return "user=${show(input.user)} password stored=${input.passwordStored()}"
        }
    }

    @GraphQLInput
    static class LoginInput {
        ArgumentValue<String> user
        private String storedPassword

        // write-only: Jackson sets it, nothing may read it
        void setPassword(String password) {
            this.storedPassword = password
        }

        boolean passwordStored() {
            return storedPassword != null
        }

        // computed: no request sets it, and the library must not call it
        String getSummary() {
            throw new IllegalStateException('a computed getter was called')
        }
    }

    @Requires(property = 'spec.name', value = SPEC_WRITE_ONLY_WRAPPER)
    @GraphQLRootResolver
    static class WriteOnlyWrapperQuery {
        String update(String name, Color color, Integer limit, WriteOnlyWrapperProfileInput profile) {
            return null
        }
    }

    @GraphQLInput
    static class WriteOnlyWrapperProfileInput {
        Color color
        List<String> tags
        AddressInput address

        void setNickname(ArgumentValue<String> nickname) {
        }
    }

    @Requires(property = 'spec.name', value = SPEC_WRITE_ONLY_NESTED)
    @GraphQLRootResolver
    static class WriteOnlyNestedQuery {
        String update(String name, Color color, Integer limit, WriteOnlyNestedProfileInput profile) {
            return null
        }
    }

    @GraphQLInput
    static class WriteOnlyNestedProfileInput {
        String nickname
        Color color
        List<String> tags

        void setAddress(DetectAddressInput address) {
        }
    }

    static enum Color {
        RED,
        GREEN
    }

    @GraphQLInput
    static class ProfileInput {
        String nickname
        Color color
        List<String> tags = ['unset']
        AddressInput address
    }

    @GraphQLInput
    static class AddressInput {
        String city
        String zip
    }

    @GraphQLInput
    static class WrappedProfileInput {
        ArgumentValue<String> nickname
        Color color
        List<String> tags
        AddressInput address
    }

    @GraphQLInput
    static class DetectProfileInput {
        ArgumentValue<String> nickname
        ArgumentValue<Color> color
        List<String> tags = ['unset']
        ArgumentValue<DetectAddressInput> address
    }

    @GraphQLInput
    static class DetectAddressInput {
        ArgumentValue<String> city
        String zip
    }

}
