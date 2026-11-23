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
package io.micronaut.graphql.tools.benchmarks.fixture;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.binding.internal.finding.Finding;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaModel;
import io.micronaut.graphql.tools.visitor.BindingRulesAccess;
import io.micronaut.inject.ast.ClassElement;

import javax.annotation.processing.Processor;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Compiler {@link ClassElement}s of a resolver class, an input class with properties and an input class with constructor arguments,
 * for the benchmarks of the per-class binding rules in the form they run at compile time. The elements come from the sources of
 * {@link BindingFixtureSources}, compiled in process with javac and Micronaut's processors, like the corpus of the processor module
 * compiles its cases.
 *
 * <p>A class element is valid only during its processing round, so the compilation runs on a thread of its own that this class
 * keeps parked inside the round ({@link FixtureCaptureVisitor#finish}) until {@link #close()}. The benchmark thread reads the
 * elements while the compiler thread waits; no compiler type reaches the runtime API, and the library is not changed.</p>
 *
 * <p>The rules run through {@link BindingRulesAccess}, the processor's internal accessor. The visitor itself is switched off in the
 * fixture's compilation ({@code checks=false}).</p>
 */
public final class ClassElementFixture implements AutoCloseable {

    /** The resolver class: two root fields with scalar arguments. */
    public static final String RESOLVER = BindingFixtureSources.PACKAGE + ".BookQuery";

    /** A resolver class with an argument of an input class that has properties. */
    public static final String INPUT = BindingFixtureSources.PACKAGE + ".FilterQuery";

    /** A resolver class with an argument of an input class that has constructor arguments. */
    public static final String CONSTRUCTOR = BindingFixtureSources.PACKAGE + ".SearchQuery";

    /** A resolver class whose method has an argument that the schema does not declare: the rules report it. */
    public static final String DRIFTED = BindingFixtureSources.PACKAGE + ".DriftQuery";

    private static volatile ClassElementFixture compiling;

    private final CompletableFuture<Map<String, ClassElement>> ready = new CompletableFuture<>();
    private final CountDownLatch release = new CountDownLatch(1);
    private final Path work;
    private final SchemaModel schema;
    private final Thread compiler;
    private Map<String, ClassElement> elements = Map.of();

    private ClassElementFixture() {
        try {
            work = Files.createTempDirectory("binding-fixture");
            Path schemaDirectory = Files.createDirectories(work.resolve("graphql"));
            Files.writeString(schemaDirectory.resolve("schema.graphqls"), BindingFixtureSources.SCHEMA);
            schema = BindingRulesAccess.schema(List.of(schemaDirectory))
                    .orElseThrow(() -> new IllegalStateException("The fixture schema cannot be read"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        compiler = new Thread(this::compile, "class-element-fixture");
        compiler.setDaemon(true);
    }

    /** @return the fixture, compiled; the elements stay valid until {@link #close()} */
    public static ClassElementFixture open() {
        ClassElementFixture fixture = new ClassElementFixture();
        Properties saved = (Properties) System.getProperties().clone();
        compiling = fixture;
        fixture.compiler.start();
        try {
            fixture.elements = fixture.ready.get(2, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException | TimeoutException e) {
            fixture.release.countDown();
            throw new IllegalStateException("The fixture sources did not compile: " + e.getMessage(), e);
        } finally {
            compiling = null;
            // Micronaut copies the -A options of a compilation into the system properties
            System.setProperties(saved);
        }
        return fixture;
    }

    @Nullable
    static ClassElementFixture compiling() {
        return compiling;
    }

    /** Called by the visitor on the compiler thread: publishes the elements and waits until the fixture is closed. */
    void holdRound(Map<String, ClassElement> captured) {
        ready.complete(captured);
        try {
            release.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void compile() {
        try {
            JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
            try (StandardJavaFileManager files = javac.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
                Path classes = Files.createDirectories(work.resolve("classes"));
                List<File> classpath = new ArrayList<>();
                for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
                    classpath.add(new File(entry));
                }
                files.setLocation(StandardLocation.CLASS_PATH, classpath);
                files.setLocation(StandardLocation.CLASS_OUTPUT, List.of(classes.toFile()));
                List<JavaFileObject> sources = new ArrayList<>();
                BindingFixtureSources.SOURCES.forEach((name, text) -> sources.add(new SimpleJavaFileObject(
                        URI.create("string:///" + BindingFixtureSources.PACKAGE + "/" + name + ".java"), JavaFileObject.Kind.SOURCE) {
                    @Override
                    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                        return text;
                    }
                }));
                JavaCompiler.CompilationTask task = javac.getTask(null, files, null,
                        List.of("-proc:full", "-parameters", "-Amicronaut.graphql.tools.processor.checks=false"), null, sources);
                List<Processor> processors = new ArrayList<>();
                ServiceLoader.load(Processor.class, ClassElementFixture.class.getClassLoader()).forEach(processors::add);
                task.setProcessors(processors);
                if (!task.call()) {
                    ready.completeExceptionally(new IllegalStateException("javac reported errors"));
                } else if (!ready.isDone()) {
                    ready.completeExceptionally(new IllegalStateException("No fixture class was visited"));
                }
            }
        } catch (IOException | RuntimeException e) {
            ready.completeExceptionally(e);
        }
    }

    /**
     * Runs the per-class rules over a fixture class the way the visitor does for the first class of a compilation: a model of its own,
     * the element read through it, and the rules of the class.
     *
     * @param className {@link #RESOLVER}, {@link #INPUT}, {@link #CONSTRUCTOR} or {@link #DRIFTED}
     * @return the findings of the class
     */
    public List<Finding> check(String className) {
        ClassElement element = elements.get(className);
        if (element == null) {
            throw new IllegalArgumentException("No fixture class " + className + ", known: " + elements.keySet());
        }
        return BindingRulesAccess.check(schema, element);
    }

    /** @return the text of the schema that the rules check the fixture against */
    public String schemaText() {
        return BindingFixtureSources.SCHEMA;
    }

    /** @return the directory with the class files and bean definitions that the compilation wrote */
    public Path classes() {
        return work.resolve("classes");
    }

    @Override
    public void close() {
        release.countDown();
        try {
            compiler.join(TimeUnit.MINUTES.toMillis(1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
