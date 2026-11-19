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
package io.micronaut.graphql.tools.processing

import groovy.transform.CompileStatic

import javax.annotation.processing.Processor
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaCompiler
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import javax.tools.StandardJavaFileManager
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

@CompileStatic
class Compilation {

    static final String SCHEMA_FACTORY = '''
package startup;

import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

@Factory
public class SchemaFactory {
    @Singleton
    TypeDefinitionRegistry typeDefinitionRegistry(@Value("${startup.sdl-dir}") String sdlDir) {
        TypeDefinitionRegistry registry = new TypeDefinitionRegistry();
        try (Stream<Path> files = Files.walk(Path.of(sdlDir))) {
            files.filter(Files::isRegularFile).sorted().forEach(file -> {
                try {
                    registry.merge(new SchemaParser().parse(Files.readString(file)));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return registry;
    }
}
'''

    static final String STARTUP = '''
package startup;

import io.micronaut.context.ApplicationContext;
import io.micronaut.graphql.tools.manifest.BindingManifest;
import java.util.Map;

public final class Startup {
    public static String manifest(Map<String, Object> properties) {
        try (ApplicationContext context = ApplicationContext.run(properties)) {
            return context.getBean(BindingManifest.class).render();
        } catch (RuntimeException e) {
            Throwable cause = e;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            return "FAILED: " + cause.getMessage();
        }
    }
}
'''

    private final Map<String, String> sources = new LinkedHashMap<>()
    private final Map<String, String> schemaFiles = new LinkedHashMap<>()
    private final Map<String, String> options = new LinkedHashMap<>()
    private final List<File> classpath = []
    private boolean sdlDir = true
    private boolean micronaut
    private boolean checks = true
    private File output
    private final List<Processor> extraProcessors = []

    Compilation schema(String path, String content) {
        schemaFiles.put(path, content)
        return this
    }

    Compilation source(String className, String content) {
        sources.put(className, content)
        return this
    }

    Compilation option(String name, String value) {
        options.put(name, value)
        return this
    }

    Compilation withoutSdlDir() {
        sdlDir = false
        return this
    }

    Compilation withMicronaut() {
        micronaut = true
        sources.put('startup.SchemaFactory', SCHEMA_FACTORY)
        sources.put('startup.Startup', STARTUP)
        return this
    }

    Compilation withMicronautOnly() {
        checks = false
        return withMicronaut()
    }

    Compilation withProcessor(Processor processor) {
        extraProcessors.add(processor)
        return this
    }

    Compilation incrementallyInto(File directory) {
        output = directory
        classpath.add(directory)
        return this
    }

    Compilation classpath(File entry) {
        classpath.add(entry)
        return this
    }

    Result compile() {
        Path work = Files.createTempDirectory('graphql-processor-test')
        Path schemaDir = work.resolve('graphql')
        Files.createDirectories(schemaDir)
        schemaFiles.each { path, content ->
            Path file = schemaDir.resolve(path)
            Files.createDirectories(file.parent)
            Files.writeString(file, content)
        }
        Path classes = output != null ? output.toPath() : work.resolve('classes')
        Files.createDirectories(classes)

        JavaCompiler compiler = ToolProvider.systemJavaCompiler
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>()
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)
        List<File> path = System.getProperty('java.class.path').split(File.pathSeparator).collect { new File(it) }
        path.addAll(classpath)
        fileManager.setLocation(StandardLocation.CLASS_PATH, path)
        fileManager.setLocation(StandardLocation.CLASS_OUTPUT, [classes.toFile()])

        List<String> arguments = ['-proc:full']
        if (sdlDir) {
            arguments.add("-A${ProcessorOptions.SDL_DIR}=${schemaDir}".toString())
        }
        options.each { name, value -> arguments.add("-A${name}=${value}".toString()) }

        List<JavaFileObject> units = sources.collect { name, content -> (JavaFileObject) new Source(name, content) }
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, arguments, null, units)
        boolean success
        if (micronaut) {
            // Micronaut's processor copies the -Amicronaut.* options into the system properties, where a later startup
            // would read them as configuration
            Properties systemProperties = (Properties) System.properties.clone()
            // the JSR-269 processor is no longer registered (#291): add it to Micronaut's processors, unless this compilation runs without it
            List<Processor> processors = ServiceLoader.load(Processor, getClass().classLoader).findAll {
                !(it instanceof GraphQLSchemaBindingProcessor)
            } as List<Processor>
            if (checks) {
                processors.add((Processor) new GraphQLSchemaBindingProcessor())
            }
            task.setProcessors(processors)
            try {
                // the visitor of this module, which the jar registers, is not under test here. It reads the option
                // from the system properties too; the JSR-269 processor would reject it as a -A argument
                System.setProperty('micronaut.graphql.tools.processor.checks', 'false')
                success = task.call()
            } finally {
                System.properties = systemProperties
            }
        } else {
            List<Processor> processors = [(Processor) new GraphQLSchemaBindingProcessor()]
            processors.addAll(extraProcessors)
            task.setProcessors(processors)
            success = task.call()
        }
        fileManager.close()
        return new Result(success, diagnostics.diagnostics, classes.toFile(), schemaDir.toFile())
    }

    @CompileStatic
    static class Result {

        final boolean success
        final List<Diagnostic<? extends JavaFileObject>> diagnostics
        final File classOutput
        final File schemaDir

        Result(boolean success, List<Diagnostic<? extends JavaFileObject>> diagnostics, File classOutput, File schemaDir) {
            this.success = success
            this.diagnostics = diagnostics
            this.classOutput = classOutput
            this.schemaDir = schemaDir
        }

        String getManifest() {
            File file = new File(classOutput, BindingManifestLines.PATH)
            return file.isFile() ? Files.readString(file.toPath(), StandardCharsets.UTF_8) : null
        }

        String getOmitted() {
            File file = new File(classOutput, BindingManifestLines.OMITTED_PATH)
            return file.isFile() ? Files.readString(file.toPath(), StandardCharsets.UTF_8) : null
        }

        String startupManifest(Map<String, Object> properties = [:]) {
            List<URL> urls = [classOutput.toURI().toURL()]
            System.getProperty('java.class.path').split(File.pathSeparator).each { urls.add(new File(it).toURI().toURL()) }
            URLClassLoader loader = new URLClassLoader(urls as URL[], ClassLoader.platformClassLoader)
            Thread thread = Thread.currentThread()
            ClassLoader previous = thread.contextClassLoader
            try {
                thread.contextClassLoader = loader
                Map<String, Object> configuration = new LinkedHashMap<>(properties)
                configuration.putIfAbsent('micronaut.graphql.tools.manifest.record', true)
                configuration.put('startup.sdl-dir', schemaDir.absolutePath)
                return legacyKinds((String) loader.loadClass('startup.Startup').getMethod('manifest', Map).invoke(null, configuration))
            } finally {
                thread.contextClassLoader = previous
                loader.close()
            }
        }

        // the JSR-269 processor (deleted by #144) still writes the kinds of the `!` lines that the startup wrote before #237
        private static String legacyKinds(String manifest) {
            Map<String, String> kinds = [
                    'unbound-resolver-method': 'unbound', 'unbound-enum-constant': 'unbound', 'unmapped-input-property': 'unbound',
                    'unused-dictionary-entry': 'unusedDictionary', 'precedence-ambiguity': 'ambiguous',
                    'unreachable-type': 'unreachable', 'unreachable-value-type': 'unreachable', 'unwired-directive': 'unwiredDirective']
            List<String> lines = manifest.readLines()
            List<String> findings = lines.findAll { it.startsWith('!') }.collect { String line ->
                String kind = line.substring(1, line.indexOf(' '))
                '!' + kinds.getOrDefault(kind, kind) + line.substring(line.indexOf(' '))
            }.sort()
            List<String> header = lines.findAll { it.startsWith('#') }
            List<String> rest = lines.findAll { !it.startsWith('#') && !it.startsWith('!') }
            return (header + findings + rest).join('\n') + (manifest.endsWith('\n') ? '\n' : '')
        }

        List<String> getErrors() {
            return diagnostics.findAll { it.kind == Diagnostic.Kind.ERROR }.collect { format(it) }
        }

        List<String> getGraphqlNotes() {
            return diagnostics.findAll { it.kind == Diagnostic.Kind.NOTE && it.getMessage(Locale.ROOT).contains(Problems.PREFIX) }
                    .collect { format(it) }
        }

        List<String> getGraphqlErrors() {
            return errors.findAll { it.contains(Problems.PREFIX) }
        }

        private static String format(Diagnostic<? extends JavaFileObject> diagnostic) {
            String message = diagnostic.getMessage(Locale.ROOT)
            if (diagnostic.source == null) {
                return message
            }
            String file = diagnostic.source.name.substring(diagnostic.source.name.lastIndexOf('/') + 1)
            return "${file}:${diagnostic.lineNumber}: ${message}".toString()
        }

        @Override
        String toString() {
            return (success ? 'success' : 'failure') + diagnostics.collect { '\n  ' + format(it) }.join('')
        }
    }

    @CompileStatic
    private static class Source extends SimpleJavaFileObject {

        private final String content

        Source(String className, String content) {
            super(URI.create('string:///' + className.replace('.', '/') + JavaFileObject.Kind.SOURCE.extension),
                    JavaFileObject.Kind.SOURCE)
            this.content = content
        }

        @Override
        CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return content
        }
    }

}
