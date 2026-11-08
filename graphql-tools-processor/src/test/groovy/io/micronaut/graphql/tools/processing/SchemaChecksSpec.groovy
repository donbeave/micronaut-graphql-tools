package io.micronaut.graphql.tools.processing

import spock.lang.Specification

/**
 * The checks that need only the schema files and the options: the processor fails javac on what the startup would
 * report for the same schema and options, naming the schema coordinate and the schema file and line.
 */
class SchemaChecksSpec extends Specification {

    static final String HELLO_QUERY = '''
package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public class HelloQuery {
    public String hello() {
        return "world";
    }
}
'''

    static final String SCHEMA = '''schema {
  query: Query
}

type Query {
  hello: String
}
'''

    static Compilation compilation(String schema) {
        return new Compilation()
                .schema('schema.graphqls', schema)
                .source('example.HelloQuery', HELLO_QUERY)
    }

    void "a schema that matches compiles"() {
        when:
        def result = compilation(SCHEMA).compile()

        then:
        result.success
        result.graphqlErrors.isEmpty()
    }

    void "without sdl-dir a compilation with GraphQL resolvers fails and says how to set it"() {
        when:
        def result = compilation(SCHEMA).withoutSdlDir().compile()

        then:
        !result.success
        result.graphqlErrors == ['[graphql] micronaut.graphql.tools.sdlDir is not set. The compilation has GraphQL '
                                         + 'resolvers or types to check against the schema: pass the directory of the '
                                         + 'schema files with -Amicronaut.graphql.tools.sdlDir=<dir> and declare it as '
                                         + 'an input of the compilation.']
    }

    void "without sdl-dir a compilation with nothing to check compiles"() {
        when:
        def result = new Compilation()
                .withoutSdlDir()
                .source('example.Clock', '''
package example;

@jakarta.inject.Singleton
public class Clock {
    public long now() {
        return 0L;
    }
}
''')
                .compile()

        then:
        result.success
    }

    void "an sdl-dir that is not a directory fails the compilation"() {
        when:
        def result = compilation(SCHEMA).option(ProcessorOptions.SDL_DIR, '/does/not/exist').compile()

        then:
        !result.success
        result.graphqlErrors == ['[graphql] micronaut.graphql.tools.sdlDir is not a directory: /does/not/exist.']
    }

    void "a schema file that does not parse fails the compilation and names the file"() {
        when:
        def result = compilation('schema { query: Query }\ntype Query {\n  hello: \n}\n').compile()

        then:
        !result.success
        result.graphqlErrors.size() == 1
        result.graphqlErrors[0].startsWith('[graphql] the schema file schema.graphqls is not valid: ')
    }

    void "a schema without a schema definition fails the compilation"() {
        when:
        def result = compilation('type Query {\n  hello: String\n}\n').compile()

        then:
        !result.success
        result.graphqlErrors == ['[graphql] the schema has no schema definition (schema { query: ... }); the library '
                                         + 'needs one to find the root operation types.']
    }

    void "an unreachable object type fails the compilation with its coordinate, file and line"() {
        when:
        def result = compilation(SCHEMA + '''
type Report {
  title: String
}
''').compile()

        then:
        !result.success
        result.graphqlErrors == ['[graphql] Report: the object type is unreachable: no field reachable from a root '
                                         + 'operation type returns it. Use it, remove it from the schema, or list it in '
                                         + 'micronaut.graphql.tools.allowUnreachable. (schema.graphqls:9)']
    }

    void "allow-unreachable allows an unreachable type"() {
        when:
        def result = compilation(SCHEMA + 'type Report {\n  title: String\n}\n')
                .option(ProcessorOptions.ALLOW_UNREACHABLE, 'Report')
                .compile()

        then:
        result.success
    }

    void "unreachable-types=ignore ignores unreachable types"() {
        when:
        def result = compilation(SCHEMA + 'type Report {\n  title: String\n}\nenum Color {\n  RED\n}\n')
                .option(ProcessorOptions.UNREACHABLE_TYPES, 'ignore')
                .compile()

        then:
        result.success
    }

    void "an allow-unreachable entry that is reachable or undefined fails the compilation"() {
        when:
        def result = compilation(SCHEMA)
                .option(ProcessorOptions.ALLOW_UNREACHABLE, 'Query,Ghost')
                .compile()

        then:
        !result.success
        result.graphqlErrors == [
                '[graphql] micronaut.graphql.tools.allowUnreachable lists Ghost, which the schema does not define.',
                '[graphql] micronaut.graphql.tools.allowUnreachable lists Query, which is not an unreachable object, '
                        + 'interface, union, enum, input or scalar type.'
        ]
    }

    void "strict mode: an unused enum, input type and scalar fail; a type a directive definition uses is reached"() {
        when:
        def result = compilation(SCHEMA + '''
enum Color {
  RED
}

input Filter {
  name: String
}

scalar Money

enum Role {
  ADMIN
}

directive @auth(role: Role) on FIELD_DEFINITION
''').compile()

        then:
        !result.success
        result.graphqlErrors == [
                '[graphql] Color: the enum is unreachable: no field, argument or input field reachable from a root '
                        + 'operation type uses it. Use it, remove it from the schema, or list it in '
                        + 'micronaut.graphql.tools.allowUnreachable. (schema.graphqls:9)',
                '[graphql] Filter: the input type is unreachable: no field, argument or input field reachable from a '
                        + 'root operation type uses it. Use it, remove it from the schema, or list it in '
                        + 'micronaut.graphql.tools.allowUnreachable. (schema.graphqls:13)',
                '[graphql] Money: the scalar is unreachable: no field, argument or input field reachable from a root '
                        + 'operation type uses it. Use it, remove it from the schema, or list it in '
                        + 'micronaut.graphql.tools.allowUnreachable. (schema.graphqls:17)'
        ]
    }

    void "with strict false unused enum, input and scalar types are accepted, as at startup"() {
        when:
        def result = compilation(SCHEMA + 'enum Color {\n  RED\n}\ninput Filter {\n  name: String\n}\nscalar Money\n')
                .option(ProcessorOptions.STRICT, 'false')
                .compile()

        then:
        result.success
    }

    void "a type reached through an interface, a union, an argument and an input field is not unreachable"() {
        when:
        def result = compilation('''schema {
  query: Query
}

type Query {
  hello: String
  search(filter: Filter): [Result]
  node: Node
}

input Filter {
  range: Range
}

input Range {
  from: Int
}

union Result = Book | Author

type Book {
  title: String
}

type Author {
  name: String
}

interface Node {
  id: ID
}

type Shelf implements Node {
  id: ID
}
''').compile()

        then:
        result.success
    }

    void "an applied directive that the schema does not declare fails the compilation; built-in directives are declared"() {
        when:
        def result = compilation('''schema {
  query: Query
}

type Query {
  hello: String @cached
  old: String @deprecated(reason: "use hello")
}
''').compile()

        then:
        !result.success
        result.graphqlErrors == ['[graphql] Query.hello: the directive @cached is applied but not declared; declare it '
                                         + 'with directive @cached on ... or remove it. (schema.graphqls:6)']
    }

    void "a resolver directive that the schema does not declare fails the compilation"() {
        when:
        def result = compilation(SCHEMA).option(ProcessorOptions.RESOLVER_DIRECTIVES, 'computed').compile()

        then:
        !result.success
        result.graphqlErrors == ['[graphql] micronaut.graphql.tools.resolverDirectives lists the directive computed, '
                                         + 'which the schema does not declare.']
    }

    void "a boolean option reads as Micronaut reads it (#value), and a value Micronaut would read as false fails"() {
        when:
        def result = compilation(SCHEMA.replace('type Query {', 'type Orphan {\n  name: String\n}\n\ntype Query {'))
                .option(ProcessorOptions.STRICT, value).option(ProcessorOptions.ALLOW_UNREACHABLE, 'Orphan').compile()

        then:
        result.success == valid
        result.graphqlErrors == (valid ? [] : ['[graphql] micronaut.graphql.tools.strict must be true or false (or yes/no, '
                                                       + "on/off), not ${value}.".toString()])

        where:
        value   | valid
        'true'  | true
        'YES'   | true
        'on'    | true
        'no'    | true
        'off'   | true
        'ture'  | false
        '1'     | false
    }

    void "an unknown option under the prefix fails, so that a typo is not silent"() {
        when:
        def result = compilation(SCHEMA).option('micronaut.graphql.tools.stirct', 'false').compile()

        then:
        !result.success
        result.graphqlErrors.size() == 1
        result.graphqlErrors[0].startsWith('[graphql] unknown option -Amicronaut.graphql.tools.stirct. The options are [')
    }

    void "an invalid option value fails the compilation"() {
        when:
        def result = compilation(SCHEMA).option(ProcessorOptions.PRECEDENCE, 'first').compile()

        then:
        !result.success
        result.graphqlErrors == ['[graphql] micronaut.graphql.tools.precedence must be strict, property-first or '
                                         + 'resolver-first, not first.']
    }

    void "schema files in subdirectories are read and merged"() {
        when:
        def result = new Compilation()
                .schema('schema.graphqls', 'schema {\n  query: Query\n}\n')
                .schema('query/query.graphqls', 'type Query {\n  hello: String\n  report: Report\n}\n')
                .schema('query/report.graphql', 'type Report {\n  title: String\n}\ntype Orphan {\n  x: Int\n}\n')
                .source('example.HelloQuery', HELLO_QUERY)
                .compile()

        then:
        !result.success
        result.graphqlErrors == ['[graphql] Orphan: the object type is unreachable: no field reachable from a root '
                                         + 'operation type returns it. Use it, remove it from the schema, or list it in '
                                         + 'micronaut.graphql.tools.allowUnreachable. (query/report.graphql:4)']
    }

    void "the processor claims the library and bean annotations, never *"() {
        expect:
        !new GraphQLSchemaBindingProcessor().supportedAnnotationTypes.contains('*')
        new GraphQLSchemaBindingProcessor().supportedAnnotationTypes.containsAll([
                'io.micronaut.graphql.tools.annotation.GraphQLRootResolver',
                'io.micronaut.graphql.tools.annotation.GraphQLTypeResolver',
                'io.micronaut.graphql.tools.annotation.GraphQLType',
                'jakarta.inject.Singleton'
        ])
        getClass().classLoader.getResource('META-INF/gradle/incremental.annotation.processors').text.trim() ==
                'io.micronaut.graphql.tools.processing.GraphQLSchemaBindingProcessor,aggregating'
    }

}
