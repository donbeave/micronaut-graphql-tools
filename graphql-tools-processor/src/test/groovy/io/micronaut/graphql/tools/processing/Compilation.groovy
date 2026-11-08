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

/**
 * Compiles Java sources in process with {@link GraphQLSchemaBindingProcessor} as the only annotation processor, against
 * schema files written to a temporary directory, and collects what javac reports. The classpath is the test classpath,
 * so the sources can use the library's annotations, Micronaut's and graphql-java's types.
 *
 * <pre>
 * def result = new Compilation()
 *         .schema('schema.graphqls', 'schema { query: Query } type Query { hello: String }')
 *         .source('example.HelloQuery', 'package example; ...')
 *         .option('micronaut.graphql.tools.strict', 'false')
 *         .compile()
 * result.success
 * result.errors   // ["HelloQuery.java:7: [graphql] Query.hello: ..."]
 * </pre>
 */
@CompileStatic
class Compilation {

    /**
     * The schema of a compilation {@link #withMicronaut()}, parsed at startup from the schema directory, one file after
     * the other.
     */
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

    /**
     * Starts the application of a compilation {@link #withMicronaut()} and returns its binding manifest, or the error
     * of the startup.
     */
    static final String STARTUP = '''
package startup;

import io.micronaut.context.ApplicationContext;
import io.micronaut.graphql.tools.BindingManifest;
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

    /**
     * @param path    the path of the file, relative to the schema directory
     * @param content the schema
     */
    Compilation schema(String path, String content) {
        schemaFiles.put(path, content)
        return this
    }

    /**
     * @param className the binary-free, dotted name of the top-level class, such as {@code example.HelloQuery}
     * @param content   its source
     */
    Compilation source(String className, String content) {
        sources.put(className, content)
        return this
    }

    /**
     * @param name  an option name, without {@code -A}
     * @param value its value
     */
    Compilation option(String name, String value) {
        options.put(name, value)
        return this
    }

    /**
     * Compiles without passing {@code micronaut.graphql.tools.sdlDir}.
     */
    Compilation withoutSdlDir() {
        sdlDir = false
        return this
    }

    /**
     * Compiles with every annotation processor of the test classpath, Micronaut's included, as a build that applies
     * this processor next to Micronaut's does, so that {@link Result#startupManifest} can start the result.
     */
    Compilation withMicronaut() {
        micronaut = true
        sources.put('startup.SchemaFactory', SCHEMA_FACTORY)
        sources.put('startup.Startup', STARTUP)
        return this
    }

    /**
     * Compiles {@link #withMicronaut()} but without this processor: only the startup checks the bindings.
     */
    Compilation withMicronautOnly() {
        checks = false
        return withMicronaut()
    }

    /**
     * @param processor a processor to run next to this one, for example one that generates sources
     */
    Compilation withProcessor(Processor processor) {
        extraProcessors.add(processor)
        return this
    }

    /**
     * Compiles into a class output that exists already and is on the classpath too, as an incremental compilation does:
     * the classes of an earlier compilation there are unchanged classes of the module, not sources.
     *
     * @param directory the class output of an earlier compilation
     */
    Compilation incrementallyInto(File directory) {
        output = directory
        classpath.add(directory)
        return this
    }

    /**
     * @param entry a directory or jar to add to the classpath, for example the class output of an earlier compilation
     */
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
            if (!checks) {
                task.setProcessors(ServiceLoader.load(Processor, getClass().classLoader).findAll {
                    !(it instanceof GraphQLSchemaBindingProcessor)
                })
            }
            try {
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

    /**
     * The outcome of a compilation.
     */
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

        /**
         * @return the binding manifest the processor wrote to the class output, or {@code null} if it wrote none
         */
        String getManifest() {
            File file = new File(classOutput, BindingManifestLines.PATH)
            return file.isFile() ? Files.readString(file.toPath(), StandardCharsets.UTF_8) : null
        }

        /**
         * @return the record of what the manifest leaves out, or {@code null} if the processor wrote none
         */
        String getOmitted() {
            File file = new File(classOutput, BindingManifestLines.OMITTED_PATH)
            return file.isFile() ? Files.readString(file.toPath(), StandardCharsets.UTF_8) : null
        }

        /**
         * Starts the compiled application (a compilation {@link Compilation#withMicronaut()}) with the schema files of
         * the compilation and returns the binding manifest of the startup, as {@code BindingManifest#render()}, or
         * {@code FAILED: <message>} if the startup fails. The application runs in a class loader of its own, so that
         * Micronaut's caches (the bean introspections) see only its classes.
         *
         * @param properties the configuration, such as {@code micronaut.graphql.tools.strict: false}
         */
        String startupManifest(Map<String, Object> properties = [:]) {
            List<URL> urls = [classOutput.toURI().toURL()]
            System.getProperty('java.class.path').split(File.pathSeparator).each { urls.add(new File(it).toURI().toURL()) }
            URLClassLoader loader = new URLClassLoader(urls as URL[], ClassLoader.platformClassLoader)
            Thread thread = Thread.currentThread()
            ClassLoader previous = thread.contextClassLoader
            try {
                thread.contextClassLoader = loader
                Map<String, Object> configuration = new LinkedHashMap<>(properties)
                configuration.put('startup.sdl-dir', schemaDir.absolutePath)
                return (String) loader.loadClass('startup.Startup').getMethod('manifest', Map).invoke(null, configuration)
            } finally {
                thread.contextClassLoader = previous
                loader.close()
            }
        }

        /**
         * @return the errors, each as {@code File.java:line: message} when javac reports it on a Java element, or as
         * the message alone
         */
        List<String> getErrors() {
            return diagnostics.findAll { it.kind == Diagnostic.Kind.ERROR }.collect { format(it) }
        }

        /**
         * @return the notes of the processor (those starting with {@code [graphql]}): what it left to the startup
         */
        List<String> getGraphqlNotes() {
            return diagnostics.findAll { it.kind == Diagnostic.Kind.NOTE && it.getMessage(Locale.ROOT).contains(Problems.PREFIX) }
                    .collect { format(it) }
        }

        /**
         * @return the errors of the processor (those starting with {@code [graphql]}), formatted as {@link #getErrors()}
         */
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
