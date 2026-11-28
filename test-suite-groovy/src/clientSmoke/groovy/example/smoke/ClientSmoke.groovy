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
package example.smoke

import groovy.transform.CompileStatic
import io.micronaut.context.ApplicationContext

/**
 * The native smoke test of the client (#681): Spock cannot run in a native image, so this executable runs the scenarios of the
 * client tests of the `serdeTest` source set against the running native executable of the application, whose address is the
 * only argument. It exits with a non-zero status on the first failure.
 */
@CompileStatic
class ClientSmoke {

    static void main(String[] args) {
        String url = args[0]
        int status = 1
        URI address = URI.create(url)
        // a client of the path "/" resolves against the server address of the configuration; a client of a service id, against its urls
        ApplicationContext context = ApplicationContext.run([
                'micronaut.server.host'               : address.host,
                'micronaut.server.port'               : address.port,
                'micronaut.http.services.example.urls': url
        ] as Map<String, Object>)
        try {
            int scenarios = context.getBean(SmokeScenarios).run()
            System.out.println("Client smoke test passed: ${scenarios} scenarios against ${url}")
            status = 0
        } catch (Throwable failure) {
            failure.printStackTrace(System.out)
        } finally {
            context.close()
        }
        System.exit(status)
    }
}
