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
package io.micronaut.graphql.tools.scalars

import io.micronaut.context.ApplicationContext
import io.micronaut.graphql.tools.mapping.GraphQLScalarMapping
import spock.lang.Specification
import spock.lang.Unroll

/** A configuration value that a scalar cannot use fails the startup, and the message names the key. */
class ScalarConfigurationSpec extends Specification {

    @Unroll
    void "#key = '#value' fails with a message that names the key"() {
        when:
        def context = ApplicationContext.run([('micronaut.graphql.tools.scalars.' + key): value, 'micronaut.graphql.tools.eager-init': false], 'test')
        try {
            context.getBeansOfType(GraphQLScalarMapping)
        } finally {
            context.close()
        }

        then:
        def e = thrown(Exception)
        e.message.contains('micronaut.graphql.tools.scalars.' + key)

        where:
        key                    | value
        'local-date.name'      | '2Date'
        'local-date.name'      | 'Local-Date'
        'local-date.name'      | '__Date'
        'local-date.name'      | ' '
        'local-date.name'      | ''
        'local-date.format'    | 'not a pattern {'
        'local-date.format'    | ' '
        'duration.format'      | 'dd/MM/yyyy'
        'period.format'        | 'seconds'
        'json.format'          | 'iso'
        'uuid.format'          | 'iso'
        'positive-int.format'  | 'iso'
    }

    void "an unknown scalar id lists the valid ids"() {
        when:
        def context = ApplicationContext.run(['micronaut.graphql.tools.scalars.local-dat.enabled': false,
                                              'micronaut.graphql.tools.eager-init': false], 'test')
        try {
            context.getBeansOfType(GraphQLScalarMapping)
        } finally {
            context.close()
        }

        then:
        def e = thrown(Exception)
        e.message.contains('micronaut.graphql.tools.scalars.local-dat')
        StandardScalar.values().every { e.message.contains(it.getId()) }
    }

    @Unroll
    void "the misspelled property #key under a valid scalar id fails the startup and is named with the valid ones"() {
        when:
        def context = ApplicationContext.run([('micronaut.graphql.tools.scalars.' + key): 'x', 'micronaut.graphql.tools.eager-init': false], 'test')
        try {
            context.getBeansOfType(GraphQLScalarMapping)
        } finally {
            context.close()
        }

        then:
        def e = thrown(Exception)
        e.message.contains('micronaut.graphql.tools.scalars.' + key)
        ['enabled', 'name', 'format'].every { e.message.contains(it) }

        where:
        key << ['local-date.nmae', 'uuid.enable', 'json.fromat', 'instant.formats']
    }

    void "the configuration binds enabled, name and format of each scalar"() {
        when:
        def context = ApplicationContext.run(['micronaut.graphql.tools.scalars.local-date.name'   : 'Date',
                                              'micronaut.graphql.tools.scalars.local-date.format' : 'dd/MM/yyyy',
                                              'micronaut.graphql.tools.scalars.uuid.enabled'      : false,
                                              'micronaut.graphql.tools.eager-init'                : false], 'test')
        ScalarsConfiguration configuration = context.getBean(ScalarsConfiguration)
        def names
        try {
            names = context.getBeansOfType(GraphQLScalarMapping)*.name
        } finally {
            context.close()
        }

        then:
        configuration.enabled
        configuration.scalars.keySet() == ['local-date', 'uuid'] as Set
        configuration.get(StandardScalar.LOCAL_DATE).name == 'Date'
        configuration.get(StandardScalar.LOCAL_DATE).format == 'dd/MM/yyyy'
        !configuration.get(StandardScalar.UUID).enabled
        configuration.get(StandardScalar.INSTANT) == null
        names.contains('Date')
        !names.contains('UUID')
        !names.contains('LocalDate')
        names.size() == StandardScalar.values().size() - 1
    }

    void "the master switch turns the module off"() {
        when:
        def context = ApplicationContext.run(['micronaut.graphql.tools.scalars.enabled': false,
                                              'micronaut.graphql.tools.eager-init': false], 'test')
        ScalarsConfiguration configuration = context.getBean(ScalarsConfiguration)
        def mappings
        try {
            mappings = context.getBeansOfType(GraphQLScalarMapping)
        } finally {
            context.close()
        }

        then:
        !configuration.enabled
        mappings.empty
        ScalarsConfiguration.ENABLED_CONFIG == 'micronaut.graphql.tools.scalars.enabled'
    }

    void "the formats of the scalars that accept the keyword iso accept it, in any case"() {
        when:
        def context = ApplicationContext.run(['micronaut.graphql.tools.scalars.instant.format'   : 'ISO',
                                              'micronaut.graphql.tools.scalars.local-date.format': 'iso',
                                              'micronaut.graphql.tools.eager-init'               : false], 'test')
        def names
        try {
            names = context.getBeansOfType(GraphQLScalarMapping)*.name
        } finally {
            context.close()
        }

        then:
        names.containsAll(['Instant', 'LocalDate'])
    }

}
