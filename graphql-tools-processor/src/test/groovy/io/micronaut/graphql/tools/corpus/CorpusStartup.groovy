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
package io.micronaut.graphql.tools.corpus

import graphql.GraphQL
import graphql.language.SourceLocation
import groovy.transform.CompileStatic
import io.micronaut.context.ApplicationContext
import io.micronaut.graphql.tools.manifest.BindingManifest
import io.micronaut.graphql.tools.binding.BindingCode
import io.micronaut.graphql.tools.internal.exceptions.AbstractMappingException
import io.micronaut.graphql.tools.internal.exceptions.BindingFailuresException
import io.micronaut.graphql.tools.internal.exceptions.checks.InvalidSchemaCoordinateException
import io.micronaut.graphql.tools.internal.exceptions.implementation.AmbiguousTypeClassException
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.graphql.tools.internal.exceptions.checks.StrictModeException
import io.micronaut.graphql.tools.internal.exceptions.input.UnmappedInputPropertiesException
import io.micronaut.graphql.tools.internal.exceptions.checks.UnreachableTypesException
import io.micronaut.graphql.tools.internal.exceptions.wiring.UnwiredDirectivesException

import java.util.regex.Matcher

/**
 * Starts the application of a compiled case with the library and reports what the startup says. It is loaded by the
 * class loader of the application (see {@link RuntimeBinder}), so that Micronaut's caches see only the classes of the
 * case, and it talks to the runner in JDK types only: a map with {@code findings} (a list of
 * {@code [code, coordinate, location]}), {@code message} (the message of the binding failure), {@code manifest} and {@code failure}.
 *
 * <p>The codes are the check names for the strict-mode checks and the simple name of the exception for the
 * other failures, until the stable codes of #237 land.</p>
 */
@CompileStatic
final class CorpusStartup {

    private static final String SCHEMA_COORDINATE = /@GraphQLSchemaCoordinate\("([^"]+)"\)/

    private static final String TYPE_REF = 'kind name ofType { kind name ofType { kind name ofType { kind name ofType { kind name } } } }'

    private static final String INTROSPECTION = '{ __schema { queryType { name } mutationType { name } subscriptionType { name } ' +
            'types { kind name interfaces { name } possibleTypes { name } enumValues(includeDeprecated: true) { name } ' +
            "inputFields { name type { ${TYPE_REF} } } " +
            "fields(includeDeprecated: true) { name type { ${TYPE_REF} } args { name type { ${TYPE_REF} } } } } } }"

    private CorpusStartup() {
    }

    private static Object normalize(Object value) {
        if (value instanceof Map) {
            Map<String, Object> sorted = new TreeMap<>()
            ((Map<String, Object>) value).each { String key, Object item -> sorted.put(key, normalize(item)) }
            return sorted
        }
        if (value instanceof List) {
            List<Object> items = ((List<Object>) value).collect { normalize(it) }
            if (!items.empty && items.every { it instanceof Map && ((Map) it).containsKey('name') && ((Map) it).get('name') != null }) {
                items.sort { ((Map) it).get('name').toString() }
            }
            return items
        }
        return value
    }

    /**
     * The properties of the application of a case. The websocket keep-alive of micronaut-graphql is {@code @Scheduled} and runs on a
     * background thread at startup; when it fails there, Micronaut stops the context and the case reports a binding that was not
     * made. The cases have no transport, so it is off (as in the library's test properties and the benchmarks); a case can turn it on.
     */
    static Map<String, Object> properties(Map<String, String> config, List<String> schemaLocations) {
        Map<String, Object> properties = new LinkedHashMap<>(config)
        properties.put('micronaut.graphql.tools.schema.locations', schemaLocations)
        properties.putIfAbsent('micronaut.graphql.tools.manifest.enabled', true)
        properties.putIfAbsent('micronaut.graphql.tools.eager-init', false)
        properties.putIfAbsent('graphql.graphql-ws.enabled', false)
        return properties
    }

    static Map<String, Object> start(Map<String, String> config, List<String> environments, List<String> schemaLocations) {
        return start(config, environments, schemaLocations, false)
    }

    /**
     * @param introspect also run the introspection query and report it, normalized (every list of named elements sorted by name), as
     *                   {@code responses}: what the drift mutation runner compares between a schema and its equivalent
     */
    static Map<String, Object> start(Map<String, String> config, List<String> environments, List<String> schemaLocations, boolean introspect) {
        Map<String, Object> properties = properties(config, schemaLocations)
        Map<String, Object> result = new LinkedHashMap<>()
        List<List<String>> findings = []
        result.put('findings', findings)
        ApplicationContext context = null
        try {
            // the start is guarded too: with eager-init=true the binding runs while the context starts
            context = ApplicationContext.builder(properties, environments as String[]).start()
            GraphQL graphQl = context.getBean(GraphQL)
            if (introspect) {
                result.put('responses', normalize(graphQl.execute(INTROSPECTION).toSpecification()).toString())
            }
            // the manifest bean cannot be read when the case turns recording off: a bound startup then has no manifest
            if (String.valueOf(properties.get('micronaut.graphql.tools.manifest.enabled')) == 'true') {
                String manifest = context.getBean(BindingManifest).render()
                result.put('manifest', manifest.readLines().findAll { !it.startsWith('#') }.join('\n') + '\n')
            }
        } catch (RuntimeException e) {
            // an internal subclass (the unexpected-binding failure) is a binding failure too
            Throwable known = causes(e).find { it instanceof SchemaBindingException }
            if (known == null) {
                result.put('failure', causes(e).last().toString())
            } else {
                findings.addAll(flatten(known))
                result.put('message', known.message)
            }
        } finally {
            context?.close()
        }
        return result
    }

    private static List<Throwable> causes(Throwable throwable) {
        List<Throwable> chain = []
        for (Throwable t = throwable; t != null && !chain.contains(t); t = t.cause) {
            chain.add(t)
        }
        return chain
    }

    private static List<List<String>> flatten(Throwable failure) {
        if (failure instanceof BindingFailuresException) {
            return failure.failures.collectMany { flatten(it) }
        } else if (failure instanceof AmbiguousTypeClassException) {
            return [[failure.getCode().getCode(), failure.typeName, '-']]
        } else if (failure instanceof AbstractMappingException) {
            SourceLocation at = failure.mappingContext.sourceLocation
            return [[failure.getCode().getCode(), failure.mappingContext.coordinate, location(at)]]
        } else if (failure instanceof StrictModeException) {
            List<List<String>> problems = []
            failure.problems.eachWithIndex { String problem, int index -> problems.add(classify(failure.codes[index], problem)) }
            return problems
        } else if (failure instanceof UnreachableTypesException) {
            Map<String, String> kinds = [:]
            Matcher matcher = (failure.message =~ /(\w+) \((type|interface|union|enum|input|scalar)\)/)
            while (matcher.find()) {
                kinds.put(matcher.group(1), matcher.group(2))
            }
            return failure.typeNames.collect { String type ->
                [kinds.get(type) in ['enum', 'input', 'scalar'] ? 'unreachable-value-type' : 'unreachable-type', type, '-']
            }
        } else if (failure instanceof UnwiredDirectivesException) {
            return failure.unwiredDirectives.collect { String name -> ['unwired-directive', name, '-'] } +
                    failure.invalidInertDirectives.collect { String name -> ['invalid-inert-directives', name, '-'] } +
                    failure.invalidSchemaCustomizerDirectives.collect { String name ->
                        ['invalid-schema-customizer-directives', name, '-']
                    }
        } else if (failure instanceof UnmappedInputPropertiesException) {
            return failure.unmappedProperties.collectMany { String type, Map.Entry<Class<?>, List<String>> entry ->
                entry.value.collect { String property -> ['unmapped-input-property', type + '.' + property, '-'] }
            }
        } else if (failure instanceof InvalidSchemaCoordinateException) {
            return failure.problems.collect { String problem ->
                Matcher matcher = (problem =~ SCHEMA_COORDINATE)
                [failure.getCode().getCode(), matcher.find() ? matcher.group(1) : '-', '-']
            }
        }
        String code = ((SchemaBindingException) failure).getCode().getCode()
        // the exceptions that list their findings as lines: the first token of a line is the coordinate or the class
        List<String> lines = listOf(failure, 'getProblems') ?: listOf(failure, 'getAmbiguities')
        if (lines != null) {
            return lines.collect { String line -> [code, line.split(/[\s:]/)[0], '-'] }
        }
        // the exceptions that name the affected types or directives
        List<String> names = (listOf(failure, 'getGraphQlTypes') ?: listOf(failure, 'getDirectiveNames'))
        if (names != null && !names.empty) {
            return names.collect { String name -> [code, name, '-'] }
        }
        return [[code, '-', '-']]
    }

    private static List<String> listOf(Throwable failure, String getter) {
        try {
            Object value = failure.getClass().getMethod(getter).invoke(failure)
            return value == null ? null : (value as Collection<?>).collect { String.valueOf(it) }
        } catch (NoSuchMethodException ignored) {
            return null
        }
    }

    /** A strict-mode problem: its code and, by the sentence of the check, the coordinate it is about. */
    private static List<String> classify(BindingCode code, String problem) {
        Matcher m = (problem =~ /^The (?:resolver method|root resolver method|enum constant|SchemaMappingDictionary maps|type|directive) (\S+)/)
        return [code.getCode(), m.find() ? m.group(1) : '-', '-']
    }

    private static String location(SourceLocation at) {
        if (at == null || at.line < 0) {
            return '-'
        }
        return "${at.sourceName ?: 'schema'}:${at.line}:${at.column}".toString()
    }

}
