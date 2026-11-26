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

/** Loads {@link ClientMetadataReader} in a class loader that has the compiled classes, and returns what it read. */
@CompileStatic
final class ClientMetadataLoader {

    private ClientMetadataLoader() {
    }

    static Map<String, Map<String, Object>> read(File classes, String type) {
        List<URL> urls = [classes.toURI().toURL()]
        System.getProperty('java.class.path').split(File.pathSeparator).each { urls.add(new File(it).toURI().toURL()) }
        URLClassLoader loader = new URLClassLoader(urls as URL[], ClassLoader.platformClassLoader)
        Thread thread = Thread.currentThread()
        ClassLoader previous = thread.contextClassLoader
        try {
            thread.contextClassLoader = loader
            return (Map<String, Map<String, Object>>) loader.loadClass(ClientMetadataReader.name).getMethod('read', String).invoke(null, type)
        } finally {
            thread.contextClassLoader = previous
            loader.close()
        }
    }
}
