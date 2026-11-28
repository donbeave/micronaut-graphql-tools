/*
 * Copyright 2021-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.graphql.tools.maven

import io.micronaut.graphql.tools.codegen.Language
import io.micronaut.graphql.tools.codegen.Mode
import io.micronaut.graphql.tools.codegen.ModelStyle
import io.micronaut.graphql.tools.codegen.NameConstants
import io.micronaut.graphql.tools.codegen.SealedAbstractTypes
import io.micronaut.graphql.tools.codegen.Options
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugin.testing.MojoRule
import org.junit.Rule
import spock.lang.Specification
import spock.lang.TempDir

import org.apache.maven.artifact.DefaultArtifact
import org.apache.maven.artifact.handler.DefaultArtifactHandler

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The binding of a pom configuration to the parameters of the mojo, and the source root that the goal registers for each language.
 * The harness reads the hand-written plugin.xml, so a wrong type, default or expression in it fails here.
 */
class GenerateMojoSpec extends Specification {

    @Rule
    MojoRule rule = new MojoRule()

    @TempDir
    Path dir

    def "a configuration that sets only the package has the default of every other option"() {
        when:
        def built = MojoProject.create(rule, dir, '<packageName>example.api</packageName>')

        then:
        built.mojo.options() == Options.builder().packageName('example.api').build()
        built.mojo.@schemaDirectory == dir.resolve('src/main/resources/graphql').toFile()
        built.mojo.@outputDirectory == dir.resolve('target/generated-sources/graphql-tools').toFile()
        !built.mojo.@skip
    }

    def "every option of a configuration reaches the options record"() {
        when:
        def built = MojoProject.create(rule, dir, '''
            <language>KOTLIN</language>
            <packageName>example.api</packageName>
            <modelPackage>example.m</modelPackage>
            <resolverPackage>example.r</resolverPackage>
            <namesPackage>example.n</namesPackage>
            <generatedAnnotation>jakarta.annotation.Generated</generatedAnnotation>
            <modelStyle>BEAN</modelStyle>
            <interfaceTypes><interfaceType>Node</interfaceType><interfaceType>Entity</interfaceType></interfaceTypes>
            <sealedAbstractTypes>NEVER</sealedAbstractTypes>
            <typeNamePrefix>Gql</typeNamePrefix>
            <typeNameSuffix>Dto</typeNameSuffix>
            <nullableAnnotation>example.Nullable</nullableAnnotation>
            <rootContracts>false</rootContracts>
            <contractNameTemplate>{Operation}{Field}</contractNameTemplate>
            <kotlinSuspend>true</kotlinSuspend>
            <subscriptionReturnType>kotlinx.coroutines.flow.Flow</subscriptionReturnType>
            <typeMappings><Date>java.time.LocalDate</Date><Book.title>example.Title</Book.title></typeMappings>
            <nameConstants>TYPES</nameConstants>
            <includes><include>**/*.gql</include></includes>
            <excludes><exclude>**/draft/**</exclude></excludes>
            <commentsAsDescriptions>true</commentsAsDescriptions>
            <operationIncludes><operationInclude>ops/**/*.graphql</operationInclude></operationIncludes>
            <operationExcludes><operationExclude>**/draft/**</operationExclude></operationExcludes>
        ''')

        then:
        built.mojo.options() == Options.builder()
                .language(Language.KOTLIN).mode(Mode.SERVER).packageName('example.api').modelPackage('example.m')
                .resolverPackage('example.r').namesPackage('example.n').generatedAnnotation('jakarta.annotation.Generated')
                .modelStyle(ModelStyle.BEAN).interfaceTypes(['Node', 'Entity']).sealedAbstractTypes(SealedAbstractTypes.NEVER).typeNamePrefix('Gql').typeNameSuffix('Dto')
                .nullableAnnotation('example.Nullable').rootContracts(false).contractNameTemplate('{Operation}{Field}')
                .kotlinSuspend(true).subscriptionReturnType('kotlinx.coroutines.flow.Flow')
                .typeMappings([Date: 'java.time.LocalDate', 'Book.title': 'example.Title'])
                .nameConstants(NameConstants.TYPES).includes(['**/*.gql']).excludes(['**/draft/**'])
                .commentsAsDescriptions(true).operationIncludes(['ops/**/*.graphql']).operationExcludes(['**/draft/**']).build()
    }

    def "the string form of the parameters is the string form of the options"() {
        when:
        def built = MojoProject.create(rule, dir, '''
            <language>GROOVY</language><packageName>example.api</packageName>
            <typeMappings><Date>java.time.LocalDate</Date></typeMappings>
            <excludes><exclude>a</exclude></excludes>
        ''')

        then:
        Options.fromMap(built.mojo.optionMap()).options().toMap() == built.mojo.options().toMap()
        built.mojo.options().toMap() == [language: 'GROOVY', packageName: 'example.api', 'typeMappings.Date': 'java.time.LocalDate',
                                         'excludes.size': '1', 'excludes.0': 'a']
    }

    def "an empty element is an absent value, so none stands for no annotation"() {
        when:
        def empty = MojoProject.create(rule, dir, '<packageName>example.api</packageName><generatedAnnotation></generatedAnnotation>')
        def built = MojoProject.create(rule, dir, '<packageName>example.api</packageName><generatedAnnotation>none</generatedAnnotation><nullableAnnotation>none</nullableAnnotation>')

        then:
        empty.mojo.options().generatedAnnotation() == Options.DEFAULT_GENERATED_ANNOTATION
        built.mojo.options().generatedAnnotation() == ''
        built.mojo.options().nullableAnnotation() == ''
    }

    def "an invalid option value fails the goal and names the option"() {
        when:
        MojoProject.create(rule, dir, '<packageName>example.api</packageName><modelStyle>CLASS</modelStyle>').mojo.options()

        then:
        thrown(MojoFailureException)
    }

    def "the source root of #language is its directory under the output directory"() {
        given:
        def built = MojoProject.create(rule, dir, "<language>${language}</language><packageName>example.api</packageName>",
                ['src/main/resources/graphql/schema.graphqls': 'type Query { a: String }'])

        when:
        built.mojo.execute()

        then:
        built.project.compileSourceRoots.contains(dir.resolve("target/generated-sources/graphql-tools/${directory}").toString())

        where:
        language | directory
        'JAVA'   | 'java'
        'KOTLIN' | 'kotlin'
        'GROOVY' | 'groovy'
    }

    def "a custom output directory is the base of the source root"() {
        given:
        def built = MojoProject.create(rule, dir,
                '<packageName>example.api</packageName><outputDirectory>${project.basedir}/gen</outputDirectory>',
                ['src/main/resources/graphql/schema.graphqls': 'type Query { a: String }'])

        when:
        built.mojo.execute()

        then:
        built.project.compileSourceRoots.contains(dir.resolve('gen/java').toString())
    }

    def "skip leaves the project untouched"() {
        given:
        def built = MojoProject.create(rule, dir, '<skip>true</skip>')

        when:
        built.mojo.execute()

        then:
        built.project.compileSourceRoots == [dir.resolve('src/main/java').toString()]
        !java.nio.file.Files.exists(dir.resolve('target'))
    }

    def "a missing schema directory fails the goal"() {
        given:
        def built = MojoProject.create(rule, dir, '<packageName>example.api</packageName>')

        when:
        built.mojo.execute()

        then:
        def e = thrown(MojoFailureException)
        e.message.contains('schema directory')
    }

    def "a syntax error in a schema file fails the goal and writes nothing"() {
        given:
        def built = MojoProject.create(rule, dir, '<packageName>example.api</packageName>',
                ['src/main/resources/graphql/schema.graphqls': 'type Query {'])

        when:
        built.mojo.execute()

        then:
        thrown(MojoFailureException)
        !java.nio.file.Files.exists(dir.resolve('target/generated-sources/graphql-tools/.graphql-tools-generated'))
    }

    def "an empty selection is a warning, removes the stale files and does not fail"() {
        given:
        def output = dir.resolve('target/generated-sources/graphql-tools')
        def built = MojoProject.create(rule, dir, '<packageName>example.api</packageName>',
                ['src/main/resources/graphql/notes.txt': 'not a schema'])
        GeneratedSources.reconcile(output, 'old', ['java/example/Old.java': 'class Old {}'])

        when:
        built.mojo.execute()

        then:
        noExceptionThrown()
        !java.nio.file.Files.exists(output.resolve('java/example/Old.java'))
        GeneratedSources.readManifest(output).files() == []
    }

    def "an unreadable schema file fails the goal with the diagnostic code"() {
        given:
        def built = MojoProject.create(rule, dir, '<packageName>example.api</packageName>',
                ['src/main/resources/graphql/schema.graphqls': 'type Query { a: String }'])
        def file = dir.resolve('src/main/resources/graphql/schema.graphqls')
        java.nio.file.Files.setPosixFilePermissions(file, [] as Set)
        org.junit.Assume.assumeFalse('a file owner who can read anything', java.nio.file.Files.isReadable(file))

        when:
        built.mojo.execute()

        then:
        def e = thrown(MojoFailureException)
        e.message.contains('[schema-file-unreadable]')

        cleanup:
        java.nio.file.Files.setPosixFilePermissions(file, ['OWNER_READ', 'OWNER_WRITE'].collect { java.nio.file.attribute.PosixFilePermission.valueOf(it) } as Set)
    }

    def "a missing package name fails the goal even when no schema file matches"() {
        given:
        def built = MojoProject.create(rule, dir, '', ['src/main/resources/graphql/notes.txt': 'nothing'])

        when:
        built.mojo.execute()

        then:
        def e = thrown(MojoFailureException)
        e.message.contains('invalid')
        !java.nio.file.Files.exists(dir.resolve('target/generated-sources/graphql-tools'))
    }

    def "an unreadable subdirectory of the schema directory fails the goal with the diagnostic code"() {
        given:
        def built = MojoProject.create(rule, dir, '<packageName>example.api</packageName>',
                ['src/main/resources/graphql/schema.graphqls': 'type Query { a: String }',
                 'src/main/resources/graphql/locked/other.graphqls': 'type Other { a: String }'])
        def locked = dir.resolve('src/main/resources/graphql/locked')
        java.nio.file.Files.setPosixFilePermissions(locked, [] as Set)
        org.junit.Assume.assumeFalse('a file owner who can list anything', java.nio.file.Files.isReadable(locked))

        when:
        built.mojo.execute()

        then:
        def e = thrown(MojoFailureException)
        e.message.contains('[schema-file-unreadable]')

        cleanup:
        java.nio.file.Files.setPosixFilePermissions(locked, java.nio.file.attribute.PosixFilePermissions.fromString('rwx------'))
    }

    def "a bracket in a glob is a literal character and a directory wildcard matches no directory, as in the generator"() {
        given:
        def built = MojoProject.create(rule, dir,
                '<packageName>example.api</packageName><includes><include>a/**/b[1].graphqls</include></includes>',
                ['src/main/resources/graphql/a/b[1].graphqls': 'type Query { a: String }',
                 'src/main/resources/graphql/a/x/b1.graphqls': 'this is not graphql'])

        when:
        built.mojo.execute()

        then:
        noExceptionThrown()
        GeneratedSources.readManifest(dir.resolve('target/generated-sources/graphql-tools')) != null
    }

    private MojoProject withDependency(String configuration) {
        def jar = dir.resolve('libs/lib.jar')
        Files.createDirectories(jar.parent)
        new ZipOutputStream(Files.newOutputStream(jar)).withCloseable { zip ->
            zip.putNextEntry(new ZipEntry('META-INF/micronaut-graphql-tools/type-mappings.properties'))
            zip.write('Pet=org.lib.Pet\nMoney=org.lib.Money\n'.getBytes('UTF-8'))
            zip.closeEntry()
        }
        def built = MojoProject.create(rule, dir, "<packageName>example.api</packageName>${configuration}",
                ['src/main/resources/graphql/schema.graphqls': 'type Query { pet: Pet  price: Money }\nscalar Money'])
        def artifact = new DefaultArtifact('org.lib', 'lib', '1.0', 'compile', 'jar', null, new DefaultArtifactHandler(type: 'jar', addedToClasspath: true))
        artifact.file = jar.toFile()
        built.project.artifacts = [artifact] as Set
        built
    }

    def "a type that a dependency of the compile class path maps needs no typeMappings entry, and the mappings file of the module is a resource"() {
        given:
        def built = withDependency('')

        when:
        built.mojo.execute()

        then:
        def resources = dir.resolve('target/generated-sources/graphql-tools/resources')
        built.project.resources*.directory.contains(resources.toString())
        Files.readString(resources.resolve('META-INF/micronaut-graphql-tools/type-mappings.properties')).contains('Money=org.lib.Money')
    }

    def "with typeMappingsFromDependencies off the dependencies are not read"() {
        given:
        def built = withDependency('<typeMappingsFromDependencies>false</typeMappingsFromDependencies>')

        when:
        built.mojo.execute()

        then:
        def failure = thrown(MojoFailureException)
        failure.message.contains('code generation failed')
    }

    def "the classes of the project itself are not given to the generator"() {
        given:
        def built = MojoProject.create(rule, dir, '<packageName>example.api</packageName>',
                ['src/main/resources/graphql/schema.graphqls': 'type Query { pet: Pet }\ntype Pet { name: String }'])
        def own = Files.createDirectories(dir.resolve('target/classes/META-INF/micronaut-graphql-tools'))
        Files.writeString(own.resolve('type-mappings.properties'), 'Pet=org.stale.Pet\n')

        expect:
        built.mojo.dependencyPaths().every { !it.startsWith(dir.resolve('target/classes')) }
    }
}
