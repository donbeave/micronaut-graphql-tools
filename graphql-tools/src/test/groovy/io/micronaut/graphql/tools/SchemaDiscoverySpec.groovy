package io.micronaut.graphql.tools

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.binding.BindingCode
import io.micronaut.graphql.tools.binding.internal.schema.SchemaIndex
import io.micronaut.graphql.tools.exceptions.SchemaDiscoveryException
import io.micronaut.graphql.tools.schema.SchemaDiscovery
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class SchemaDiscoverySpec extends Specification {

    static final String SPEC_NAME = 'SchemaDiscoverySpec'
    static final String REGISTRY_SPEC_NAME = 'SchemaDiscoverySpec.registry'

    @TempDir
    Path dir

    ApplicationContext context

    void cleanup() {
        context?.close()
    }

    void "several files and no schema block start without a factory, and an operation file is skipped"() {
        given:
        Files.writeString(dir.resolve('query.graphqls'), 'type Query { hello: String lib: Lib }')
        Files.createDirectories(dir.resolve('types'))
        Files.writeString(dir.resolve('types/lib.graphqls'), 'type Lib { name: String }')
        Files.writeString(dir.resolve('client.graphql'), 'query { hello }')

        when:
        context = start(['micronaut.graphql.tools.schema.locations': ["file:${dir}/**/*".toString()]], getClass().classLoader)

        then:
        run('{ hello lib { name } }') == [hello: 'hello', lib: [name: 'lib']]
    }

    void "a schema in a dependency jar is read by classpath*: and ignored by classpath:"() {
        given:
        Path jar = jar('lib.jar', ['discovery/lib.graphqls': 'type Lib { name: String }'])
        ClassLoader loader = new URLClassLoader([jar.toUri().toURL()] as URL[], getClass().classLoader)

        when:
        context = start(['micronaut.graphql.tools.schema.locations': ['classpath*:discovery/**/*.graphqls']], loader)

        then:
        run('{ lib { name } }') == [lib: [name: 'lib']]

        and:
        SchemaDiscovery.discover(['classpath:discovery/**/*.graphqls'], loader).read() == ['discovery/app.graphqls']
        SchemaDiscovery.discover(['classpath*:discovery/**/*.graphqls'], loader).read() ==
                ['discovery/app.graphqls', 'lib.jar!/discovery/lib.graphqls']
    }

    void "the index gives the same files as listing the classpath"() {
        given:
        Path jar = jar('lib.jar', ['discovery/lib.graphqls'                 : 'type Lib { name: String }',
                                    (SchemaIndex.RESOURCE): SchemaIndex.render(['discovery/lib.graphqls'])])
        ClassLoader loader = new URLClassLoader([jar.toUri().toURL()] as URL[], getClass().classLoader)
        Path appIndex = dir.resolve(SchemaIndex.RESOURCE)
        Files.createDirectories(appIndex.parent)
        Files.writeString(appIndex, SchemaIndex.render(['discovery/app.graphqls', 'discovery/hello.graphql']))
        ClassLoader both = new URLClassLoader([dir.toUri().toURL()] as URL[], loader)

        when:
        def listed = SchemaDiscovery.discover(['classpath*:discovery/**/*.graphqls'], both, false)
        def indexed = SchemaDiscovery.discover(['classpath*:discovery/**/*.graphqls'], both, true)

        then:
        indexed.read().size() == 2
        indexed.registry().types().keySet() == listed.registry().types().keySet()
        indexed.registry().getType('Lib').isPresent()
        indexed.registry().getType('Query').isPresent()
    }

    void "a jar without directory entries is read through its index next to a listed root, and no file is read twice"() {
        given:
        Path jar = jar('flat.jar', ['discovery/lib.graphqls'                 : 'type Lib { name: String }',
                                    (SchemaIndex.RESOURCE): SchemaIndex.render(['discovery/lib.graphqls'])], false)
        Path appIndex = dir.resolve(SchemaIndex.RESOURCE)
        Files.createDirectories(appIndex.parent)
        Files.writeString(appIndex, SchemaIndex.render(['discovery/app.graphqls']))
        ClassLoader loader = new URLClassLoader([dir.toUri().toURL(), jar.toUri().toURL()] as URL[], getClass().classLoader)

        expect:
        SchemaDiscovery.discover(['classpath*:discovery/**/*.graphqls'], loader, false).registry().getType('Lib').isPresent()
        SchemaDiscovery.discover(['classpath*:discovery/**/*.graphqls'], loader, false).read().size() == 2
    }

    void "an index entry that is not on the classpath fails and names it"() {
        given:
        Path appIndex = dir.resolve(SchemaIndex.RESOURCE)
        Files.createDirectories(appIndex.parent)
        Files.writeString(appIndex, SchemaIndex.render(['discovery/app.graphqls', 'discovery/gone.graphqls']))
        ClassLoader loader = new URLClassLoader([dir.toUri().toURL()] as URL[], getClass().classLoader)

        when:
        SchemaDiscovery.discover(['classpath*:discovery/**/*.graphqls'], loader, true)

        then:
        def e = thrown(SchemaDiscoveryException)
        e.code() == BindingCode.SCHEMA_NOT_FOUND
        e.message.contains('discovery/gone.graphqls')
    }

    void "a wildcard classpath location in a native image needs the index and says so"() {
        when:
        SchemaDiscovery.discover(['classpath:nothing-here/**/*.graphqls'], getClass().classLoader, true)

        then:
        def e = thrown(SchemaDiscoveryException)
        e.message.contains(SchemaIndex.RESOURCE)
    }

    void "no file at all names the locations and the way out"() {
        when:
        SchemaDiscovery.discover(['classpath:nothing-here/**/*.graphqls'], getClass().classLoader, false)

        then:
        def e = thrown(SchemaDiscoveryException)
        e.message.contains('classpath:nothing-here/**/*.graphqls')
        e.message.contains('TypeDefinitionRegistry bean')
    }

    void "files that are all empty or operation-only fail as not found and name the skipped files"() {
        given:
        Files.writeString(dir.resolve('empty.graphqls'), '# nothing\n')
        Files.writeString(dir.resolve('client.graphqls'), 'query { hello }')

        when:
        SchemaDiscovery.discover(["file:${dir}/*.graphqls".toString()], getClass().classLoader)

        then:
        def e = thrown(SchemaDiscoveryException)
        e.code() == BindingCode.SCHEMA_NOT_FOUND
        e.message.contains('client.graphqls')
        e.message.contains('empty.graphqls')
    }

    void "a literal location that is missing fails, and an invalid location is reported with its key"() {
        when:
        SchemaDiscovery.discover([location], getClass().classLoader, false)

        then:
        def e = thrown(SchemaDiscoveryException)
        e.message.contains(part)

        where:
        location                          | part
        'classpath:discovery/none.graphqls' | 'is not on the classpath'
        'file:/does/not/exist.graphqls'   | 'does not exist'
        'graphql/*.graphqls'              | 'micronaut.graphql.tools.schema.locations'
    }

    void "a file that does not parse is named"() {
        given:
        Files.writeString(dir.resolve('broken.graphqls'), 'type Query { a: }')

        when:
        SchemaDiscovery.discover(["file:${dir}/*.graphqls".toString()], getClass().classLoader, false)

        then:
        def e = thrown(SchemaDiscoveryException)
        e.code() == BindingCode.SCHEMA_INVALID
        e.message.contains('broken.graphqls')
    }

    void "an application TypeDefinitionRegistry bean is used instead of the files"() {
        when:
        context = ApplicationContext.builder(['spec.name': SPEC_NAME,
                                              'micronaut.graphql.tools.schema.locations': ['classpath:nothing/**/*.graphqls']])
                .singletons(new graphql.schema.idl.SchemaParser().parse('type Query { hello: String lib: Lib } type Lib { name: String }'))
                .start()

        then:
        context.getBean(graphql.GraphQL).execute('{ hello }').getData() == [hello: 'hello']
    }

    void "an application registry bean is the only registry candidate, so the discovery is never created"() {
        when:
        context = ApplicationContext.builder(['spec.name': REGISTRY_SPEC_NAME, 'micronaut.graphql.tools.eager-init': false,
                                              'micronaut.graphql.tools.schema.locations': ['classpath:nothing/**/*.graphqls']]).start()

        then:
        context.getBeansOfType(graphql.schema.idl.TypeDefinitionRegistry).size() == 1
    }

    void "overlapping file locations read a file once"() {
        given:
        Files.createDirectories(dir.resolve('common'))
        Files.writeString(dir.resolve('query.graphqls'), 'type Query { a: Int }')
        Files.writeString(dir.resolve('common/lib.graphqls'), 'type Lib { name: String }')

        when:
        def loaded = SchemaDiscovery.discover(["file:${dir}/**/*.graphqls".toString(), "file:${dir}/common/*.graphqls".toString()],
                getClass().classLoader)

        then:
        loaded.read() == ['common/lib.graphqls', 'query.graphqls']
    }

    void "a visible classpath root without schema files shadows a later root and its index"() {
        given:
        Path first = dir.resolve('first')
        Files.createDirectories(first.resolve('discovery'))
        Files.writeString(first.resolve('discovery/client.graphql'), 'query { a }')
        Path jar = jar('lib.jar', ['discovery/lib.graphqls'                 : 'type Lib { name: String }',
                                    (SchemaIndex.RESOURCE): SchemaIndex.render(['discovery/lib.graphqls'])])
        ClassLoader loader = new URLClassLoader([first.toUri().toURL(), jar.toUri().toURL()] as URL[], ClassLoader.platformClassLoader)

        when:
        SchemaDiscovery.discover(['classpath:discovery/**/*.graphqls'], loader, false)

        then:
        thrown(SchemaDiscoveryException)
    }

    void "without a registry bean and without files the startup fails with the locations"() {
        when:
        context = ApplicationContext.builder(['spec.name': SPEC_NAME, 'micronaut.graphql.tools.eager-init': false,
                                              'micronaut.graphql.tools.schema.locations': ['classpath:nothing/**/*.graphqls']]).start()
        context.getBean(graphql.GraphQL)

        then:
        def e = thrown(BeanInstantiationException)
        e.message.contains('classpath:nothing/**/*.graphqls') || e.cause?.message?.contains('classpath:nothing/**/*.graphqls') ||
                causes(e).any { it instanceof SchemaDiscoveryException }
    }

    private static List<Throwable> causes(Throwable t) {
        def list = []
        for (Throwable c = t; c != null && !list.contains(c); c = c.cause) {
            list << c
        }
        list
    }

    private ApplicationContext start(Map config, ClassLoader loader) {
        ApplicationContext.builder(['spec.name': SPEC_NAME] << config).classLoader(loader).start()
    }

    private Object run(String query) {
        def result = context.getBean(graphql.GraphQL).execute(query)
        assert result.errors.isEmpty(): result.errors
        result.getData()
    }

    private Path jar(String name, Map<String, String> entries, boolean directoryEntries = true) {
        Path jar = dir.resolve(name)
        Set<String> directories = [] as Set
        new JarOutputStream(Files.newOutputStream(jar)).withCloseable { out ->
            entries.each { path, text ->
                for (int slash = path.indexOf('/'); slash >= 0; slash = path.indexOf('/', slash + 1)) {
                    String directory = path.substring(0, slash + 1)
                    if (directoryEntries && directories.add(directory)) {
                        out.putNextEntry(new JarEntry(directory))
                        out.closeEntry()
                    }
                }
                out.putNextEntry(new JarEntry(path))
                out.write(text.getBytes('UTF-8'))
                out.closeEntry()
            }
        }
        jar
    }

    @Requires(property = 'spec.name', value = REGISTRY_SPEC_NAME)
    @io.micronaut.context.annotation.Factory
    static class RegistryFactory {
        @jakarta.inject.Singleton
        graphql.schema.idl.TypeDefinitionRegistry registry() {
            new graphql.schema.idl.SchemaParser().parse('type Query { hello: String }')
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        String hello() {
            'hello'
        }

        Lib lib() {
            new Lib(name: 'lib')
        }
    }

    @GraphQLType
    static class Lib {
        String name
    }
}
