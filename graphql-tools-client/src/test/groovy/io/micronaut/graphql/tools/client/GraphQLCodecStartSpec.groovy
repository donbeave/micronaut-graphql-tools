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
package io.micronaut.graphql.tools.client

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanProvider
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.graphql.tools.client.internal.serde.GraphQLCodec
import io.micronaut.serde.ObjectMapper
import spock.lang.Specification

class GraphQLCodecStartSpec extends Specification {

    void "an application without a Micronaut Serialization mapper fails when the codec is created, and the message names the module to add"() {
        given:
        def mappers = Mock(BeanProvider)
        mappers.isPresent() >> false

        when:
        new GraphQLCodec(mappers)

        then:
        def e = thrown(ConfigurationException)
        e.message.contains('Micronaut Serialization')
        e.message.contains('io.micronaut.serde:micronaut-serde-jackson')
    }

    void "an application that has the mapper starts without creating the codec, and creates it on demand"() {
        when:
        def context = ApplicationContext.run()

        then:
        context.containsBean(ObjectMapper)
        context.getBean(GraphQLCodec) != null

        cleanup:
        context.close()
    }
}
