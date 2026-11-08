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

    private final Map<String, String> sources = new LinkedHashMap<>()
    private final Map<String, String> schemaFiles = new LinkedHashMap<>()
    private final Map<String, String> options = new LinkedHashMap<>()
    private final List<File> classpath = []
    private boolean sdlDir = true
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
     * @param processor a processor to run next to this one, for example one that generates sources
     */
    Compilation withProcessor(Processor processor) {
        extraProcessors.add(processor)
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
        Path classes = work.resolve('classes')
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
        List<Processor> processors = [(Processor) new GraphQLSchemaBindingProcessor()]
        processors.addAll(extraProcessors)
        task.setProcessors(processors)
        boolean success = task.call()
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
