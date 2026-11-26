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
package io.micronaut.graphql.tools.visitor

import groovy.transform.CompileStatic
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.VisitorContext

import java.nio.file.Files
import java.nio.file.Path

/**
 * The compile-time harness of the six codes that only the annotation processor raises: each scenario compiles a small source
 * and returns the message of the compile error, as {@link ProcessorCodesSpec} asserts it and as the error reference shows it.
 */
@CompileStatic
final class ProcessorCodeScenarios {

    static final String SCHEMA = 'type Query { ping: String }\n'

    static final String RESOLVER = '''package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import jakarta.inject.Singleton;

@Singleton
@GraphQLRootResolver
public class QueryResolver {

    public String ping() {
        return "pong";
    }

}'''

    /** The scenarios whose message does not depend on the machine; the name is what the page writes after {@code // compile: }. */
    static final Map<String, String> CODES = [
            'unknown-option'        : 'processor-option-unknown',
            'invalid-option'        : 'processor-option-invalid',
            'removed-option'        : 'processor-option-removed',
            'schema-not-configured' : 'processor-schema-not-configured',
            'schema-directory-missing': 'processor-schema-not-found',
            'internal-error'        : 'processor-internal-error',
    ]

    private ProcessorCodeScenarios() {
    }

    private static VisitorCompilation compilation() {
        return new VisitorCompilation().schema('schema.graphqls', SCHEMA).source('example.QueryResolver', RESOLVER)
    }

    /** @return the message of the one compile error of the scenario, without the stack trace of an internal error */
    static String message(String scenario) {
        List<String> errors
        switch (scenario) {
            case 'unknown-option':
                errors = compilation().option('micronaut.graphql.tools.processor.schema.dir', '/x').compile().errors
                break
            case 'invalid-option':
                errors = compilation().option(ProcessorOptions.CHECKS, 'maybe').compile().errors
                break
            case 'removed-option':
                errors = compilation().option('micronaut.graphql.tools.sdlDir', '/x').compile().errors
                break
            case 'schema-not-configured':
                errors = new VisitorCompilation().withoutSchemaOption().source('example.QueryResolver', RESOLVER).compile().errors
                break
            case 'schema-directory-missing':
                errors = new VisitorCompilation().withoutSchemaOption().option(ProcessorOptions.SCHEMA_DIRS, '/no/such/dir')
                        .source('example.QueryResolver', RESOLVER).compile().errors
                break
            case 'internal-error':
                errors = internalError()
                break
            default:
                throw new IllegalArgumentException('no scenario ' + scenario)
        }
        assert errors.size() == 1: errors
        List<String> lines = errors[0].readLines()*.stripTrailing()
        // the message of an internal error carries the stack trace between the sentence and the way out
        return (scenario == 'internal-error' ? lines.findAll { it == lines.first() || it.contains('fix: ') } : lines).join('\n')
    }

    /** A class whose members cannot be read: the check of the class fails with an exception, which the visitor reports. */
    private static List<String> internalError() {
        Path work = Files.createTempDirectory('graphql-visitor-internal')
        Path schemas = Files.createDirectories(work.resolve('graphql'))
        Files.writeString(schemas.resolve('schema.graphqls'), SCHEMA)
        List<String> errors = []
        Map<String, String> options = [(ProcessorOptions.SCHEMA_DIRS): schemas.toString()]
        VisitorContext context = [
                getOptions        : { -> options },
                getProjectDir     : { -> Optional.empty() },
                getClassesOutputPath: { -> Optional.empty() },
                fail              : { Object... arguments -> errors.add((String) arguments[0]) },
        ] as VisitorContext
        ClassElement element = [
                getName                  : { -> 'example.QueryResolver' },
                hasStereotype            : { Object... arguments -> true },
                hasDeclaredStereotype    : { Object... arguments -> arguments[0] == 'jakarta.inject.Singleton' },
                hasDeclaredAnnotation    : { Object... arguments -> false },
                isInterface              : { -> false },
                isAbstract               : { -> false },
                isEnum                   : { -> false },
                isRecord                 : { -> false },
                getEnclosedElements      : { Object... arguments -> throw new IllegalStateException('the members cannot be read') },
        ] as ClassElement
        GraphQLToolsVisitor visitor = new GraphQLToolsVisitor()
        visitor.start(context)
        visitor.visitClass(element, context)
        return errors
    }

}
