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

import groovy.transform.CompileStatic

/**
 * A class loader over the test class path without the output of this module's main source set: the visitor and its service
 * file are not there, which is a build that does not put the processor on the path of the compiler. It has the platform class loader
 * as its parent, so every other class (Micronaut's processors, the Groovy compiler) is loaded again by it, and Micronaut finds
 * the visitors of its own class loader only.
 */
@CompileStatic
final class ProcessorlessLoader {

    private static final List<String> PROCESSOR_OUTPUT = ['graphql-tools-processor/build/classes/java/main', 'graphql-tools-processor/build/resources/main']

    private static final String VISITOR = 'io.micronaut.graphql.tools.visitor.GraphQLToolsVisitor'

    private ProcessorlessLoader() {
    }

    static URLClassLoader create() {
        List<URL> urls = System.getProperty('java.class.path').split(File.pathSeparator)
                .findAll { String entry -> !PROCESSOR_OUTPUT.any { entry.replace('\\', '/').contains(it) } }
                .collect { new File(it).toURI().toURL() }
        URLClassLoader loader = new URLClassLoader(urls as URL[], ClassLoader.platformClassLoader)
        try {
            loader.loadClass(VISITOR)
        } catch (ClassNotFoundException ignored) {
            return loader
        }
        loader.close()
        throw new IllegalStateException("the visitor ${VISITOR} is on the class path of the processorless loader: the output of the processor module moved".toString())
    }

}
