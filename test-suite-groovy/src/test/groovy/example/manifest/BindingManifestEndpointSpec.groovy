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
package example.manifest

import io.micronaut.context.ApplicationContext
import io.micronaut.graphql.tools.manifest.BindingManifest
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

/**
 * Micronaut Management is not on the classpath of this suite: the recorded manifest has no endpoint, and startup is unchanged.
 */
class BindingManifestEndpointSpec extends Specification {

    def 'the manifest is recorded and no graphql-binding endpoint is registered without Micronaut Management'() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'micronaut.server.port'                     : -1,
                'micronaut.server.host'                     : '127.0.0.1',
                'micronaut.metrics.binders.executor.enabled': false,
                'micronaut.security.enabled'                : false,
                'micronaut.graphql.tools.manifest.enabled'  : true,
        ])
        HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

        expect:
        server.applicationContext.containsBean(BindingManifest)
        getClass().classLoader.getResource('io/micronaut/management/endpoint/annotation/Endpoint.class') == null

        when:
        client.toBlocking().exchange(HttpRequest.GET('/graphql-binding'))

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.NOT_FOUND

        cleanup:
        client?.close()
        server?.close()
    }

}
