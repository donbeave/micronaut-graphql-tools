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
package io.micronaut.graphql.tools.codegen.golden;

import io.micronaut.graphql.tools.codegen.GeneratedFile;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * The javac of the running JDK, with {@code --release 17} and no annotation processing, on the class path of the tests.
 */
public final class JavaInMemoryCompiler implements InMemoryCompiler {

    @Override
    public List<String> compile(List<GeneratedFile> files) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No system Java compiler: run the tests on a JDK");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<JavaFileObject> sources = new ArrayList<>();
        for (GeneratedFile file : files) {
            if (file.path().endsWith(".java")) {
                sources.add(new Source(file));
            }
        }
        try (StandardJavaFileManager standard = compiler.getStandardFileManager(diagnostics, null, null);
             Discarding manager = new Discarding(standard)) {
            Boolean ok = compiler.getTask(null, manager, diagnostics, List.of("--release", "17", "-proc:none", "-Xlint:none"), null, sources).call();
            List<String> errors = new ArrayList<>();
            for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                if (d.getKind() == Diagnostic.Kind.ERROR) {
                    String name = d.getSource() == null ? "" : d.getSource().getName() + ":" + d.getLineNumber() + ": ";
                    errors.add(name + d.getMessage(null));
                }
            }
            if (!Boolean.TRUE.equals(ok) && errors.isEmpty()) {
                errors.add("javac failed without a diagnostic");
            }
            return errors;
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static final class Source extends SimpleJavaFileObject {

        private final String content;

        Source(GeneratedFile file) {
            super(URI.create("string:///" + file.path()), Kind.SOURCE);
            this.content = file.content();
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return content;
        }
    }

    private static final class Discarding extends ForwardingJavaFileManager<StandardJavaFileManager> {

        Discarding(StandardJavaFileManager delegate) {
            super(delegate);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
            return new SimpleJavaFileObject(URI.create("mem:///" + className.replace('.', '/') + kind.extension), kind) {
                @Override
                public OutputStream openOutputStream() {
                    return new ByteArrayOutputStream();
                }
            };
        }
    }
}
