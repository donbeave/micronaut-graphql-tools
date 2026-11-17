package io.micronaut.graphql.tools.corpus

import groovy.transform.CompileStatic

import java.nio.file.Files

/**
 * The runtime backend: starts the compiled case through the real application context (the library's own beans, the
 * schema as a {@code TypeDefinitionRegistry} of the case's schema files) and reads the startup failure. The application
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
        List<String> names = []
        List<String> texts = []
        corpusCase.schemaFiles().each {
            names.add(it.fileName.toString())
            texts.add(Files.readString(it))
        }
        URLClassLoader loader = new URLClassLoader(urls as URL[], ClassLoader.platformClassLoader)
        Thread thread = Thread.currentThread()
        ClassLoader previous = thread.contextClassLoader
        try {
            thread.contextClassLoader = loader
            Map<String, Object> result = (Map<String, Object>) loader.loadClass(CorpusStartup.name)
                    .getMethod('start', Map, List, List, List)
                    .invoke(null, corpusCase.config, corpusCase.environments, names, texts)
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
