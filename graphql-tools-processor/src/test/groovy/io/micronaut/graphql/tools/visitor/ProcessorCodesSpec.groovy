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

import io.micronaut.graphql.tools.binding.BindingCode
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The compile-time harness of the codes that only the annotation processor raises (they have no startup form): each one is
 * reproduced by a compilation of a small source, with the whole message.
 */
class ProcessorCodesSpec extends Specification {

    @Unroll
    void "#scenario fails the build with #code and the message"() {
        when:
        String message = ProcessorCodeScenarios.message(scenario)

        then:
        message == expected

        where:
        scenario                   | code                              | expected
        'unknown-option'           | 'processor-option-unknown'        | '[processor-option-unknown] -: the option micronaut.graphql.tools.processor.schema.dir is not known; the options are [micronaut.graphql.tools.processor.checks, micronaut.graphql.tools.processor.client.checks, micronaut.graphql.tools.processor.client.dirs, micronaut.graphql.tools.processor.loose.nullability, micronaut.graphql.tools.processor.module.dir, micronaut.graphql.tools.processor.resolver.accessor.prefixes, micronaut.graphql.tools.processor.schema.dirs, micronaut.graphql.tools.processor.type.class.convention].'
        'invalid-option'           | 'processor-option-invalid'        | "[processor-option-invalid] -: micronaut.graphql.tools.processor.checks must be true or false (or yes/no, on/off), not 'maybe'."
        'removed-option'           | 'processor-option-removed'        | '[processor-option-removed] -: micronaut.graphql.tools.sdlDir was renamed to micronaut.graphql.tools.processor.schema.dirs: pass the schema directories there (several are joined with the path separator).'
        'schema-not-configured'    | 'processor-schema-not-configured' | "[processor-schema-not-configured] -: the schema directory is not configured, so the resolver classes cannot be checked.\n    fix: set micronaut.graphql.tools.processor.schema.dirs (see the compile-time checks of the guide for the setting of each build tool), or compile without checks with micronaut.graphql.tools.processor.checks=false"
        'schema-directory-missing' | 'processor-schema-not-found'      | '[processor-schema-not-found] -: the schema directory /no/such/dir does not exist or is not a directory.\n    fix: correct micronaut.graphql.tools.processor.schema.dirs'
    }

    void "a failure of the check of a class is reported as processor-internal-error with the cause and the way out"() {
        when:
        String message = ProcessorCodeScenarios.message('internal-error')

        then:
        message.readLines().first() == '[processor-internal-error] example.QueryResolver: the compile-time check failed on example.QueryResolver: java.lang.IllegalStateException: the members cannot be read'
        message.contains('fix: report this, and compile with micronaut.graphql.tools.processor.checks=false until it is fixed')
    }

    void "the harness has a scenario for each code that only the processor raises, and no other"() {
        expect:
        ProcessorCodeScenarios.CODES.values().toSet() == BindingCode.values().findAll { it.getCode().startsWith('processor-') }*.getCode().toSet()
    }

}
