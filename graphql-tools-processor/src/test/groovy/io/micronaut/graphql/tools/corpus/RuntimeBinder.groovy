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

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

/**
 * The runtime backend: starts the compiled case through the real application context (the library's own beans, the
 * schema read by the library's discovery from the case's schema files) and reads the startup failure. The application
 * runs in a class loader of its own, so that Micronaut's caches see only the classes of the case.
 */
@CompileStatic
final class RuntimeBinder implements Binder {

    @Override
    String getName() {
        return 'runtime'
    }

    @Override
    Verdict bind(BindRequest request) {
        CorpusCase corpusCase = request.corpusCase
        List<URL> urls = ([request.unchecked.classes] + request.unchecked.more).collect { it.toUri().toURL() }
        System.getProperty('java.class.path').split(File.pathSeparator).each { urls.add(new File(it).toURI().toURL()) }
        // the schema is found by the library's discovery, as in an application: the case directory, and a jar for the files under jar/
        List<String> locations = ["file:${corpusCase.dir.toAbsolutePath()}/*.graphqls".toString()]
        List<Path> jarFiles = corpusCase.jarSchemaFiles()
        if (!jarFiles.empty) {
            Files.createDirectories(request.work)
            Path jar = request.work.resolve('schema-dependency.jar')
            new JarOutputStream(Files.newOutputStream(jar)).withCloseable { JarOutputStream out ->
                out.putNextEntry(new JarEntry('graphql/'))
                out.closeEntry()
                jarFiles.each {
                    out.putNextEntry(new JarEntry("graphql/${it.fileName}".toString()))
                    out.write(Files.readAllBytes(it))
                    out.closeEntry()
                }
            }
            urls.add(jar.toUri().toURL())
            locations.add('classpath*:graphql/**/*.graphqls')
        }
        URLClassLoader loader = new URLClassLoader(urls as URL[], ClassLoader.platformClassLoader)
        Thread thread = Thread.currentThread()
        ClassLoader previous = thread.contextClassLoader
        try {
            thread.contextClassLoader = loader
            Map<String, Object> result = (Map<String, Object>) loader.loadClass(CorpusStartup.name)
                    .getMethod('start', Map, List, List)
                    .invoke(null, corpusCase.config, corpusCase.environments, locations)
            if (result.containsKey('failure')) {
                throw new AssertionError("${corpusCase.name} [${request.language.dir}]: the startup failed outside the bindings: ${result.get('failure')}".toString())
            }
            List<Finding> findings = ((List<List<String>>) result.get('findings')).collect { new Finding(it[0], it[1], it[2]) }
            return findings.empty ? Verdict.bound((String) result.get('manifest')) : Verdict.failed(findings)
        } finally {
            thread.contextClassLoader = previous
            loader.close()
        }
    }

}
