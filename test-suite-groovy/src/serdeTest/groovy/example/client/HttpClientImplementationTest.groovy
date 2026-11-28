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
package example.client

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class HttpClientImplementationTest extends Specification {

    @Inject
    ApplicationContext context

    def "the task selects the HTTP client implementation"() {
        given:
        String actual = context.getBean(HttpClient).getClass().name

        expect:
        if (System.getProperty('graphql.test.http-client') == 'jdk') {
            try {
                Class.forName('io.micronaut.http.client.netty.DefaultHttpClient')
                assert false : 'the Netty client is on the class path'
            } catch (ClassNotFoundException ignored) {
            }
            actual == 'io.micronaut.http.client.jdk.DefaultJdkHttpClient'
        } else {
            actual == 'io.micronaut.http.client.netty.DefaultHttpClient'
        }
    }

}
