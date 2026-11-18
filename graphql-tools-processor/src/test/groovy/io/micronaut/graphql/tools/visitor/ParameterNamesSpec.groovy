package io.micronaut.graphql.tools.visitor

import spock.lang.Specification

import javax.tools.JavaCompiler
import javax.tools.StandardJavaFileManager
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Where a parameter name comes from, at compile time and at startup (#15). A method declared in the class that Micronaut
 * processes has the name of the source, whatever the compiler flags are. A method inherited from a class file has the name
 * that the class file carries: with neither -parameters nor -g there is none and the name is {@code arg0}, and both sides
 * fail the same way.
 */
class ParameterNamesSpec extends Specification {

    private static final String SCHEMA = '''
schema { query: Query }
type Query { page(first: Int!, offset: Int!): String! }
'''

    private static final String BASE = '''
package base;
public abstract class Base {
    public String page(Integer offset, Integer first) { return "first=" + first + " offset=" + offset; }
}
'''

    private static final String QUERY = '''
package example;
@io.micronaut.graphql.tools.annotation.GraphQLRootResolver
public class Query extends base.Base {
}
'''

    private static final String OWN = '''
package example;
@io.micronaut.graphql.tools.annotation.GraphQLRootResolver
public class Query {
    public String page(Integer offset, Integer first) { return "first=" + first + " offset=" + offset; }
}
'''

    void "a method declared in the processed class binds by the name of the source without -parameters, on both sides"() {
        when:
        def checked = new VisitorCompilation().schema('schema.graphqls', SCHEMA).source('example.Query', OWN)
                .withoutParametersFlag().compile()

        then:
        checked.success
        checked.findings.isEmpty()
        started(checked) == 'bound'
    }

    void "a method inherited from a class file without parameter names fails on both sides, with the code and the coordinate"() {
        given:
        Path base = compileBase(['-proc:none'])

        when:
        def checked = new VisitorCompilation().schema('schema.graphqls', SCHEMA).source('example.Query', QUERY)
                .classpath(base.toFile()).withoutParametersFlag().compile()

        then:
        !checked.success
        checked.findings == ['[argument-mismatch] Query.page']
        checked.errors[0].contains('the parameter arg0 binds no argument of Query.page (arguments: first, offset)')
        checked.errors[0].contains('the class file of base.Base has no parameter names: compile it with -parameters')

        when:
        def unchecked = new VisitorCompilation().schema('schema.graphqls', SCHEMA).source('example.Query', QUERY)
                .classpath(base.toFile()).withoutParametersFlag()
                .option('micronaut.graphql.tools.processor.checks', 'false').compile()
        String startup = started(unchecked, base)

        then:
        unchecked.success
        startup.startsWith('argument-mismatch: ')
        startup.contains("The method parameter 'arg0' has no GraphQL argument with that name (arguments: [first, offset])")
        startup.contains('the class file has no parameter names')
        startup.contains('GraphQL coordinate: Query.page')
    }

    void "a method inherited from a class file compiled with #flag binds by name on both sides"() {
        given:
        Path base = compileBase(['-proc:none', flag])

        when:
        def checked = new VisitorCompilation().schema('schema.graphqls', SCHEMA).source('example.Query', QUERY)
                .classpath(base.toFile()).withoutParametersFlag().compile()

        then:
        checked.success
        checked.findings.isEmpty()
        started(checked, base) == 'bound'

        where:
        flag << ['-g', '-parameters']
    }

    private static String started(VisitorCompilation.Result compiled, Path... more) {
        List<URL> urls = ([compiled.classOutput] + more*.toFile()).collect { it.toURI().toURL() }
        System.getProperty('java.class.path').split(File.pathSeparator).each { urls.add(new File(it).toURI().toURL()) }
        URLClassLoader loader = new URLClassLoader(urls as URL[], ClassLoader.platformClassLoader)
        Thread thread = Thread.currentThread()
        ClassLoader previous = thread.contextClassLoader
        try {
            thread.contextClassLoader = loader
            return (String) loader.loadClass(StartupProbe.name).getMethod('start', String).invoke(null, compiled.schemaDir.absolutePath)
        } finally {
            thread.contextClassLoader = previous
            loader.close()
        }
    }

    private static Path compileBase(List<String> options) {
        Path work = Files.createTempDirectory('graphql-parameter-names')
        Path source = Files.createDirectories(work.resolve('src/base')).resolve('Base.java')
        Files.writeString(source, BASE)
        Path classes = Files.createDirectories(work.resolve('classes'))
        JavaCompiler compiler = ToolProvider.systemJavaCompiler
        StandardJavaFileManager files = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)
        try {
            files.setLocation(StandardLocation.CLASS_OUTPUT, [classes.toFile()])
            assert compiler.getTask(null, files, null, options, null, files.getJavaFileObjects(source)).call()
        } finally {
            files.close()
        }
        return classes
    }
}
