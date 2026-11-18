package io.micronaut.graphql.tools.visitor

import spock.lang.Specification

/**
 * The visitor under javac: what it reports for a class, how it reads the supertypes of a class and what it leaves to the
 * startup. The options and the failure modes of the compilation are in {@link ProcessorOptionsSpec}.
 */
class GraphQLToolsVisitorSpec extends Specification {

    static final String SCHEMA = '''\
type Query {
  book(id: ID!): Book
  books(genre: String): [Book!]!
  ping: String
  version: String
  search(term: String!, limit: Int): String
}
type Orphan {
  id: ID
}
type Mutation {
  save(title: String!): Book
  ping: String
}
type Book {
  id: ID!
  title(upper: Boolean): String
}
'''

    static final String HEADER = '''
package example;
import io.micronaut.graphql.tools.annotation.*;
import java.util.*;
import java.util.concurrent.CompletionStage;
import graphql.schema.DataFetchingEnvironment;
'''

    private static VisitorCompilation compilation(String... bodies) {
        def compilation = new VisitorCompilation().schema('schema.graphqls', SCHEMA)
        bodies.each { String body ->
            def name = (body =~ /(?:class|interface)\s+(\w+)/)[0][1]
            compilation.source('example.' + name, HEADER + body)
        }
        return compilation
    }

    def "a resolver method that binds no field of a root type is unbound, and the error carries a fix"() {
        when:
        def result = compilation('''
@GraphQLRootResolver
public class QueryResolver {
    public String book(String id) { return id; }
    public String stray(String x) { return x; }
}''').compile()

        then:
        !result.success
        result.findings == ['[unbound-resolver-method] example.QueryResolver#stray(java.lang.String)']
        result.errorFor('[unbound-resolver-method]').getMessage(Locale.ROOT).contains('fix: ')
    }

    def "the hatches: @GraphQLIgnore, a lifecycle method, a non-public method, a static method and an Object method"() {
        when:
        def result = compilation('''
@GraphQLRootResolver
public class QueryResolver {
    public String version() { return "1"; }
    @GraphQLIgnore public String helper() { return ""; }
    @jakarta.annotation.PreDestroy public void close() { }
    String packagePrivate() { return ""; }
    public static String util() { return ""; }
    @Override public String toString() { return "q"; }
}''').compile()

        then:
        result.success
        result.findings == []
    }

    def "a parameter that binds no argument is reported with the coordinate, the schema location and the member"() {
        when:
        def result = compilation('''
@GraphQLRootResolver
public class QueryResolver {
    public String books(int limit) { return ""; }
}''').compile()
        def message = result.errorFor('[argument-mismatch]').getMessage(Locale.ROOT)

        then:
        result.findings == ['[argument-mismatch] Query.books']
        message.contains('the parameter limit binds no argument of Query.books (arguments: genre)')
        message.contains('at example.QueryResolver#books(int)')
        message.contains("schema ${result.schemaDir}/schema.graphqls:3:3")
        result.errorFor('[argument-mismatch]').lineNumber > 0
    }

    def "the arguments are matched by name and by @GraphQLArgument, not by position"() {
        when:
        def result = compilation('''
@GraphQLRootResolver
public class QueryResolver {
    public String books(String genre, DataFetchingEnvironment environment) { return genre; }
    public String book(@GraphQLArgument("id") String key) { return key; }
}''').compile()

        then:
        result.success
    }

    def "reordered parameters of different types are accepted; a duplicate binding and a missing parameter are rejected"() {
        when:
        def reordered = compilation('''
@GraphQLRootResolver
public class QueryResolver {
    public String search(Integer limit, String term) { return term; }
}''').compile()
        def duplicate = compilation('''
@GraphQLRootResolver
public class QueryResolver {
    public String search(String term, @GraphQLArgument("term") String other) { return term; }
}''').compile()
        def missing = compilation('''
@GraphQLRootResolver
public class QueryResolver {
    public String search(String term) { return term; }
}''').compile()

        then:
        reordered.success
        duplicate.findings == ['[argument-mismatch] Query.search']
        duplicate.errors[0].contains('two parameters bind the argument term of Query.search')
        missing.findings == ['[argument-mismatch] Query.search']
        missing.errors[0].contains('fewer parameters than the field needs: 1 found, 2 required')
        missing.errors[0].contains('fix: declare it as search(String term, Int limit)')
    }

    def "the return type is checked against the field, after the arguments"() {
        when:
        def result = compilation('''
@GraphQLRootResolver
public class QueryResolver {
    public CompletionStage<CompletionStage<String>> version() { return null; }
    public Optional<String> book(String id) { return null; }
}''').compile()

        then:
        result.findings == ['[unsupported-return-type] Query.version']
    }

    def "a root method that binds fields of several operation types is reported once; a coordinate settles it"() {
        when:
        def result = compilation('''
@GraphQLRootResolver
public class Operations {
    public String ping() { return ""; }
}''').compile()
        def settled = compilation('''
@GraphQLRootResolver
public class Operations {
    @GraphQLSchemaCoordinate("Query.ping") public String ping() { return ""; }
}''').compile()

        then:
        result.findings == ['[root-method-several-operations] example.Operations#ping()']
        result.errorFor('[root-method-several-operations]').getMessage(Locale.ROOT).contains('Mutation.ping, Query.ping')
        settled.success
    }

    def "a coordinate that does not fit the schema is reported with the coordinate it declares"() {
        when:
        def result = compilation('''
@GraphQLRootResolver
public class QueryResolver {
    @GraphQLSchemaCoordinate("Missing.field") public String field() { return ""; }
    @GraphQLSchemaCoordinate("Query.version") public String other() { return ""; }
}''').compile()

        then:
        result.findings == ['[unknown-schema-coordinate] Missing.field', '[unknown-schema-coordinate] Query.version']
    }

    def "a type resolver is checked through its contract: the source parameter and the arguments"() {
        when:
        def result = compilation('''
public class BookModel { }''', '''
@GraphQLTypeResolver(BookModel.class)
public class BookResolver {
    @GraphQLSchemaCoordinate("Book.title") public String title(String other, Boolean upper) { return ""; }
}''', '''
@GraphQLTypeResolver(BookModel.class)
public class OtherResolver {
    public String title(BookModel book, Boolean upper) { return ""; }
    public String stray(BookModel book) { return ""; }
}''').compile()

        then:
        result.findings.sort() == ['[invalid-source-parameter] Book.title', '[unbound-resolver-method] example.OtherResolver#stray(example.BookModel)']
    }

    def "the annotations of a supertype make a subclass a resolver; an abstract class is checked through its concrete subclass"() {
        when:
        def result = compilation('''
@GraphQLRootResolver
public abstract class BaseQuery {
    public String stray() { return ""; }
}''', '''
@GraphQLRootResolver
public interface Contract {
    String stray2();
}''').compile()
        def concrete = compilation('''
@GraphQLRootResolver
public abstract class BaseQuery {
    public String stray() { return ""; }
}''', '''
public class Query extends BaseQuery {
    public String book(String id) { return id; }
}''', '''
@GraphQLRootResolver
public interface Contract {
    String stray2();
}''', '''
@jakarta.inject.Singleton
public class ContractQuery implements Contract {
    public String stray2() { return ""; }
}''').compile()

        then:
        result.success
        concrete.findings.sort() == ['[unbound-resolver-method] example.BaseQuery#stray()', '[unbound-resolver-method] example.ContractQuery#stray2()']
    }

    def "a class that only implements an annotated interface is no bean, so the startup never sees it and it is not checked"() {
        when:
        def result = compilation('''
@GraphQLRootResolver
public interface Contract {
    String stray2();
}''', '''
public class Helper implements Contract {
    public String stray2() { return ""; }
}''').compile()

        then:
        result.success
        result.notes == []
    }

    def "a type variable that a subclass binds is read as the type it is bound to, as the startup reads it"() {
        when:
        def result = compilation('''
public abstract class Base<T> {
    public CompletionStage<T> version() { return null; }
}''', '''
@GraphQLRootResolver
public class QueryResolver extends Base<CompletionStage<String>> {
}''').compile()

        then:
        result.findings == ['[unsupported-return-type] Query.version']
    }

    def "a Java method whose name starts with a dollar sign is a resolver method like any other"() {
        when:
        def result = compilation('''
@GraphQLRootResolver
public class QueryResolver {
    public String $helper() { return ""; }
}''').compile()

        then:
        result.findings == ['[unbound-resolver-method] example.QueryResolver#$helper()']
    }

    def "an @Introduction interface that is a resolver is a bean, and its abstract methods are checked"() {
        when:
        def result = new VisitorCompilation().schema('schema.graphqls', SCHEMA)
                .source('example.Answering', HEADER + '''
@io.micronaut.aop.Introduction
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
public @interface Answering { }''')
                .source('example.IntroducedQuery', HEADER + '''
@Answering
@GraphQLRootResolver
public interface IntroducedQuery {
    String version();
    String stray();
}''').compile()

        then:
        result.findings == ['[unbound-resolver-method] example.IntroducedQuery#stray()']
    }

    def "a type variable that is bound by itself is read as its bound, not followed for ever: #member"() {
        when:
        def result = compilation('''
@GraphQLRootResolver
public class QueryResolver<T extends Comparable<T>> {
''' + member + '''
}''').compile()

        then:
        result.findings == []

        where:
        member << [
                'public <E extends Enum<E>> List<E> version() { return null; }',
                'public List<T> books(String genre) { return null; }',
                'public T book(String id) { return null; }',
                'public Map<String, List<? extends Comparable<?>>> version() { return null; }',
        ]
    }

    def "an unreachable type is not a compile-time finding: reachability is whole-program, a check of the startup (#83)"() {
        when:
        def result = compilation('''
@GraphQLRootResolver
public class QueryResolver {
    public String version() { return "1"; }
}''').compile()

        then:
        result.success
    }

    def "a class that no resolver annotation reaches is not checked, and says nothing"() {
        when:
        def result = compilation()
                .source('example.Plain', HEADER + '''
public class Plain {
    public String stray() { return ""; }
}''').compile()

        then:
        result.success
        result.notes == []
    }

    def "a chain of #length input types is walked on the default stack, as the startup binds it with a work stack (#361)"() {
        given: 'one Java class backs every type of the chain, and only the last type has the field of a primitive property, so the walk must reach it to fail'
        def types = (0..<length).collect { int i ->
            "input In${i} { text: String size: Int" + (i < length - 1 ? " next: In${i + 1}" : " count: Int") + " }"
        }.join('\n')

        when:
        def result = new VisitorCompilation()
                .schema('schema.graphqls', "type Query { search(filter: In0): String }\n" + types)
                .source('example.Link', HEADER + '''
@io.micronaut.core.annotation.Introspected
public class Link {
    private String text;
    private Integer size;
    private Link next;
    private int count;
    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }
    public Link getNext() { return next; }
    public void setNext(Link next) { this.next = next; }
}''')
                .source('example.QueryResolver', HEADER + '''
@GraphQLRootResolver
public class QueryResolver {
    public String search(Link filter) { return ""; }
}''').compile()

        then:
        result.findings == ["[nullable-primitive] In${length - 1}.count".toString()]

        where:
        length << [1000, 5000]
    }
}
