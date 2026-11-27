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

import io.micronaut.graphql.tools.codegen.Options
import spock.lang.Specification

import javax.xml.parsers.DocumentBuilderFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The hand-written plugin.xml, the @Parameter fields of the mojo and the components of Options say the same thing: the same names, the
 * same defaults, one property per parameter, dotted and lowercase.
 */
class PluginDescriptorSpec extends Specification {

    static final Path SOURCES = Paths.get(System.getProperty('plugin.sources'))

    static final Set<String> PLUGIN_ONLY = ['skip', 'schemaDirectory', 'outputDirectory'] as Set
    static final Set<String> INJECTED = ['project', 'plugin'] as Set

    /** The @Parameter fields of the mojo, read from its source (the annotation is not kept in the class file). */
    static Map<String, Map> mojoParameters() {
        def source = Files.readString(SOURCES.resolve('java/io/micronaut/graphql/tools/maven/GenerateMojo.java'))
        def matcher = source =~ /(?s)@Parameter(?:\((.*?)\))?\n    private (\S+(?:<[^>]*>)?) (\w+);/
        def parameters = [:]
        matcher.each { all, annotation, rawType, name ->
            def type = rawType.replace(' ', '')
            def property = (annotation =~ /property = PROPERTY \+ "([^"]+)"/)
            def defaultValue = (annotation =~ /defaultValue = ("[^"]*"|[\w.]+)/)
            parameters[name] = [type: type, annotation: annotation ?: '', property: property ? 'micronaut.graphql.tools.codegen.' + property[0][1] : null,
                                default : defaultValue ? resolve(defaultValue[0][1]) : null]
        }
        parameters
    }

    private static String resolve(String literal) {
        literal.startsWith('"') ? literal[1..-2] : Options.getField(literal.substring('Options.'.length())).get(null).with {
            it instanceof List ? it.join(',') : it.toString()
        }
    }

    /** The descriptor: the parameters by name with their type, and the configuration with its default and expression. */
    static Map<String, Map> descriptorParameters() {
        def xml = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(PluginDescriptorSpec.getResourceAsStream('/META-INF/maven/plugin.xml')).documentElement
        def mojo = xml.getElementsByTagName('mojo').item(0)
        def parameters = [:]
        def declared = child(mojo, 'parameters').childNodes
        for (int i = 0; i < declared.length; i++) {
            def node = declared.item(i)
            if (node.nodeName == 'parameter') {
                parameters[child(node, 'name').textContent] = [type: child(node, 'type').textContent]
            }
        }
        def configuration = child(mojo, 'configuration').childNodes
        for (int i = 0; i < configuration.length; i++) {
            def node = configuration.item(i)
            if (node.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                def expression = node.textContent
                parameters[node.nodeName] += [default : node.hasAttribute('default-value') ? node.getAttribute('default-value') : null,
                                              implementation: node.getAttribute('implementation'),
                                              property: expression.startsWith('${micronaut') ? expression[2..-2] : null]
            }
        }
        parameters
    }

    private static org.w3c.dom.Node child(org.w3c.dom.Node parent, String name) {
        def nodes = parent.childNodes
        for (int i = 0; i < nodes.length; i++) {
            if (nodes.item(i).nodeName == name) {
                return nodes.item(i)
            }
        }
        null
    }

    static String dotted(String camelCase) {
        camelCase.replaceAll('([a-z0-9])([A-Z])', '$1.$2').toLowerCase(Locale.ROOT)
    }

    static String defaultOf(Options defaults, String component) {
        def value = defaults."${component}"()
        value instanceof List ? value.join(',') : value.toString()
    }

    def "every component of Options has a parameter of the same name in the mojo and in the descriptor"() {
        expect:
        Options.getRecordComponents()*.name.each { name ->
            assert mojoParameters().containsKey(name), "Options.${name} has no @Parameter in GenerateMojo"
            assert descriptorParameters().containsKey(name), "Options.${name} has no parameter in plugin.xml"
        }
    }

    def "the mojo and the descriptor have no parameter that is neither an option nor documented as plugin-only"() {
        expect:
        def allowed = Options.getRecordComponents()*.name as Set
        mojoParameters().keySet() - allowed - PLUGIN_ONLY - INJECTED == [] as Set
        descriptorParameters().keySet() - allowed - PLUGIN_ONLY - INJECTED == [] as Set
        mojoParameters().keySet() == descriptorParameters().keySet()
    }

    def "the default of every option is the default of Options, in the mojo and in the descriptor"() {
        given:
        def defaults = Options.builder().build()

        expect:
        Options.getRecordComponents()*.name.each { name ->
            def expected = name == 'nullableAnnotation' ? null : defaultOf(defaults, name)
            def mojo = mojoParameters()[name].default
            def descriptor = descriptorParameters()[name].default
            def empty = expected == '' || expected == '[]' || expected == '[:]' || expected == null
            if (empty) {
                assert mojo == null && descriptor == null, "${name}: the default is empty, but the mojo has '${mojo}' and the descriptor '${descriptor}'"
            } else {
                assert mojo == expected, "${name}: the mojo default '${mojo}' is not the Options default '${expected}'"
                assert descriptor == expected, "${name}: the descriptor default '${descriptor}' is not the Options default '${expected}'"
            }
        }
    }

    def "the plugin-only parameters have their documented defaults"() {
        expect:
        descriptorParameters().skip.default == 'false'
        descriptorParameters().schemaDirectory.default == '${project.basedir}/src/main/resources/graphql'
        descriptorParameters().outputDirectory.default == '${project.build.directory}/generated-sources/graphql-tools'
    }

    def "the property of a parameter is the dotted lowercase name under one prefix, equal in the mojo and in the descriptor"() {
        expect:
        (mojoParameters().keySet() - INJECTED).each { name ->
            def mojo = mojoParameters()[name].property
            def descriptor = descriptorParameters()[name].property
            if (name in ['interfaceTypes', 'typeMappings']) {
                assert mojo == null && descriptor == null, "${name} is a structure that a -D property cannot set"
            } else {
                def expected = 'micronaut.graphql.tools.codegen.' + dotted(name)
                assert mojo == expected, "${name}: the mojo property is ${mojo}, expected ${expected}"
                assert descriptor == expected, "${name}: the descriptor property is ${descriptor}, expected ${expected}"
            }
        }
    }

    def "the type of a parameter is the same in the mojo and in the descriptor"() {
        expect:
        mojoParameters().each { name, mojo ->
            def type = descriptorParameters()[name].type
            def expected = [boolean: 'boolean', String: 'java.lang.String', File: 'java.io.File', 'List<String>': 'java.util.List',
                            'Map<String,String>': 'java.util.Map', MavenProject: 'org.apache.maven.project.MavenProject',
                            PluginDescriptor: 'org.apache.maven.plugin.descriptor.PluginDescriptor'][mojo.type]
            assert type == expected, "${name}: the descriptor type is ${type}, the mojo type ${mojo.type}"
        }
    }

    def "the descriptor is the goal graphql-tools:generate, bound to generate-sources and thread safe"() {
        given:
        def xml = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(PluginDescriptorSpec.getResourceAsStream('/META-INF/maven/plugin.xml')).documentElement
        def mojo = xml.getElementsByTagName('mojo').item(0)

        expect:
        child(xml, 'goalPrefix').textContent == 'graphql-tools'
        child(xml, 'artifactId').textContent == 'micronaut-graphql-tools-maven-plugin'
        child(xml, 'version').textContent == System.getProperty('plugin.version')
        child(mojo, 'goal').textContent == 'generate'
        child(mojo, 'phase').textContent == 'generate-sources'
        child(mojo, 'threadSafe').textContent == 'true'
        child(mojo, 'implementation').textContent == GenerateMojo.name
    }
}
