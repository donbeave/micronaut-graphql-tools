package io.micronaut.graphql.tools.corpus

import graphql.GraphQL
import graphql.language.SourceLocation
import graphql.parser.MultiSourceReader
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import groovy.transform.CompileStatic
import io.micronaut.context.ApplicationContext
import io.micronaut.graphql.tools.BindingManifest
import io.micronaut.graphql.tools.exceptions.AbstractMappingException
import io.micronaut.graphql.tools.exceptions.BindingFailuresException
import io.micronaut.graphql.tools.exceptions.InvalidSchemaCoordinateException
import io.micronaut.graphql.tools.exceptions.StrictModeException
import io.micronaut.graphql.tools.exceptions.UnmappedInputPropertiesException
import io.micronaut.graphql.tools.exceptions.UnreachableTypesException

import java.util.regex.Matcher

/**
 * Starts the application of a compiled case with the library and reports what the startup says. It is loaded by the
 * class loader of the application (see {@link RuntimeBinder}), so that Micronaut's caches see only the classes of the
 * case, and it talks to the runner in JDK types only: a map with {@code findings} (a list of
 * {@code [code, coordinate, location]}), {@code manifest} and {@code failure}.
 *
 * <p>The codes are the check names of D-30 for the strict-mode checks and the simple name of the exception for the
 * other failures, until the stable codes of #237 land.</p>
 */
@CompileStatic
final class CorpusStartup {

    private static final String SCHEMA_COORDINATE = /@GraphQLSchemaCoordinate\("([^"]+)"\)/

    private CorpusStartup() {
    }

    static Map<String, Object> start(Map<String, String> config, List<String> environments, List<String> schemaNames,
                                     List<String> schemaTexts) {
        TypeDefinitionRegistry registry = new TypeDefinitionRegistry()
        schemaNames.eachWithIndex { String name, int index ->
            registry.merge(new SchemaParser().parse(MultiSourceReader.newMultiSourceReader().string(schemaTexts[index], name).build()))
        }
        Map<String, Object> properties = new LinkedHashMap<>(config)
        properties.putIfAbsent('micronaut.graphql.tools.manifest.record', true)
        properties.putIfAbsent('micronaut.graphql.tools.eager-init', false)
        Map<String, Object> result = new LinkedHashMap<>()
        List<List<String>> findings = []
        result.put('findings', findings)
        ApplicationContext context = null
        try {
            // the start is guarded too: with eager-init=true the binding runs while the context starts
            context = ApplicationContext.builder(properties, environments as String[]).singletons(registry).start()
            context.getBean(GraphQL)
            // the manifest bean cannot be read when the case turns recording off: a bound startup then has no manifest
            if (String.valueOf(properties.get('micronaut.graphql.tools.manifest.record')) == 'true') {
                String manifest = context.getBean(BindingManifest).render()
                result.put('manifest', manifest.readLines().findAll { !it.startsWith('#') }.join('\n') + '\n')
            }
        } catch (RuntimeException e) {
            Throwable known = causes(e).find { it.getClass().name.startsWith('io.micronaut.graphql.tools.exceptions.') }
            if (known == null) {
                result.put('failure', causes(e).last().toString())
            } else {
                findings.addAll(flatten(known))
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
        } else if (failure instanceof AbstractMappingException) {
            SourceLocation at = failure.mappingContext.sourceLocation
            return [[failure.getClass().simpleName, failure.mappingContext.coordinate, location(at)]]
        } else if (failure instanceof StrictModeException) {
            return failure.problems.collect { classify(it) }
        } else if (failure instanceof UnreachableTypesException) {
            Map<String, String> kinds = [:]
            Matcher matcher = (failure.message =~ /(\w+) \((type|interface|union|enum|input|scalar)\)/)
            while (matcher.find()) {
                kinds.put(matcher.group(1), matcher.group(2))
            }
            return failure.typeNames.collect { String type ->
                [kinds.get(type) in ['enum', 'input', 'scalar'] ? 'unreachable-value-type' : 'unreachable-type', type, '-']
            }
        } else if (failure instanceof UnmappedInputPropertiesException) {
            return failure.unmappedProperties.collectMany { String type, Map.Entry<Class<?>, List<String>> entry ->
                entry.value.collect { String property -> ['unmapped-input-property', type + '.' + property, '-'] }
            }
        } else if (failure instanceof InvalidSchemaCoordinateException) {
            return failure.problems.collect { String problem ->
                Matcher matcher = (problem =~ SCHEMA_COORDINATE)
                [failure.getClass().simpleName, matcher.find() ? matcher.group(1) : '-', '-']
            }
        }
        // the exceptions that list their findings as lines: the first token of a line is the coordinate or the class
        List<String> lines = listOf(failure, 'getProblems') ?: listOf(failure, 'getAmbiguities')
        if (lines != null) {
            return lines.collect { String line -> [failure.getClass().simpleName, line.split(/[\s:]/)[0], '-'] }
        }
        return [[failure.getClass().simpleName, '-', '-']]
    }

    private static List<String> listOf(Throwable failure, String getter) {
        try {
            return (List<String>) failure.getClass().getMethod(getter).invoke(failure)
        } catch (NoSuchMethodException ignored) {
            return null
        }
    }

    /** The strict-mode problems, by the sentence each check writes, as the check names of D-30. */
    private static List<String> classify(String problem) {
        Matcher m
        if ((m = problem =~ /^The resolver method (\S+) binds no schema field/).find()) {
            return ['unbound-resolver-method', m.group(1), '-']
        } else if ((m = problem =~ /^The root resolver method (\S+) binds fields of several operation types/).find()) {
            return ['root-method-several-operations', m.group(1), '-']
        } else if ((m = problem =~ /^The enum constant (\S+) has no value/).find()) {
            return ['unbound-enum-constant', m.group(1), '-']
        } else if ((m = problem =~ /^The SchemaMappingDictionary maps (\S+) to/).find()) {
            return ['unused-dictionary-entry', m.group(1), '-']
        } else if ((m = problem =~ /^The type (\S+) is mapped to the interface/).find()) {
            return ['interface-read-through-implementation', m.group(1), '-']
        } else if ((m = problem =~ /^The directive (\S+)/).find()) {
            return ['unwired-directive', m.group(1), '-']
        }
        return ['strict-mode', '-', '-']
    }

    private static String location(SourceLocation at) {
        if (at == null || at.line < 0) {
            return '-'
        }
        return "${at.sourceName ?: 'schema'}:${at.line}:${at.column}".toString()
    }

}
