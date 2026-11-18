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
package io.micronaut.graphql.tools.visitor;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.binding.internal.finding.Finding;
import io.micronaut.graphql.tools.binding.internal.finding.FindingRenderer;
import io.micronaut.graphql.tools.binding.internal.model.AnnotationNames;
import io.micronaut.graphql.tools.binding.internal.rules.ResolverClassFindings;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaModel;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementQuery;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The compile-time checks of the binding rules, as a visitor that runs under javac, KSP, kapt and the Groovy compiler. It is
 * isolating: the verdict for a class depends on the class, on the types it reaches and on the schema files, which a build
 * declares as an input of the compile task. It checks the concrete resolver classes, and an introduction advice interface
 * that is a resolver, with the per-class rules of {@code graphql-tools-binding}; a rule that needs the whole program (a field
 * without a method, an ambiguity, reachability, the dictionary, directives) stays at startup, because a visitor sees one class
 * at a time and cannot know the bean set or the code that completes the schema.
 *
 * <p>The state lives in one {@link Compilation} per {@link #start}: the visitor is instantiated more than once under javac
 * and the Kotlin and Gradle daemons keep classes loaded, so nothing is static and nothing is shared. The visitor does not
 * look up a class by name, does not ask for the project directory or the class output, and writes no file.</p>
 */
public final class GraphQLToolsVisitor implements TypeElementVisitor<Object, Object> {

    private static final String TAG = "[graphql-tools] ";

    @Nullable
    private Compilation compilation;

    @Override
    public @NonNull Set<String> getSupportedAnnotationNames() {
        return Set.of(AnnotationNames.ROOT_RESOLVER, AnnotationNames.TYPE_RESOLVER, AnnotationNames.SCHEMA_COORDINATE);
    }

    @Override
    public @NonNull Set<String> getSupportedOptions() {
        return ProcessorOptions.NAMES;
    }

    @Override
    public @NonNull VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public @NonNull TypeElementQuery query() {
        return TypeElementQuery.onlyClass();
    }

    @Override
    public void start(VisitorContext context) {
        Compilation started = new Compilation(ProcessorOptions.read(context.getOptions()));
        compilation = started;
        started.options.errors().forEach(it -> report(context, it, null));
        if (started.options.checks() && started.options.errors().isEmpty()) {
            started.schema.missingDirectories().forEach(it -> {
                started.failed = true;
                report(context, it, null);
            });
        }
        if (!started.options.errors().isEmpty()) {
            started.failed = true;
        }
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        Compilation current = compilation;
        if (current == null || current.failed || !current.options.checks()
                || !isResolver(element) && !hasCoordinateMethod(element) || !ElementTypeModel.isBeanClass(element)) {
            return;
        }
        try {
            check(current, element, context);
        } catch (RuntimeException e) {
            if (isControlFlow(e)) {
                throw e;
            }
            StringWriter trace = new StringWriter();
            e.printStackTrace(new PrintWriter(trace));
            current.failed = true;
            report(context, new Finding(BindingCode.PROCESSOR_INTERNAL_ERROR.code(), element.getName(),
                    "the compile-time check failed on " + element.getName() + ": " + e + "\n" + trace, null, null, null,
                    "report this, and compile with micronaut.graphql.tools.processor.checks=false until it is fixed"),
                    element);
        }
    }

    @Override
    public void finish(VisitorContext context) {
        Compilation current = compilation;
        if (current == null || current.noted) {
            return;
        }
        if (!current.options.checks() && current.options.errors().isEmpty()) {
            current.noted = true;
            context.info(TAG + "GraphQL compile-time checks are off (" + ProcessorOptions.CHECKS + "=false); startup still "
                    + "runs every check.");
        } else if (current.checked > 0 && !current.failed) {
            current.noted = true;
            context.info(TAG + "checked " + current.checked + " resolver " + (current.checked == 1 ? "class" : "classes")
                    + " against " + current.schema.files() + " schema " + (current.schema.files() == 1 ? "file" : "files")
                    + " in " + current.schema.directories() + "; " + current.findings + " "
                    + (current.findings == 1 ? "finding" : "findings") + ".");
        }
    }

    private void check(Compilation current, ClassElement element, VisitorContext context) {
        if (current.options.schemaDirs().isEmpty()) {
            current.failed = true;
            report(context, new Finding(BindingCode.PROCESSOR_SCHEMA_NOT_CONFIGURED.code(), null,
                    "the schema directory is not configured, so the resolver classes cannot be checked",
                    null, null, null, "set " + ProcessorOptions.SCHEMA_DIRS + " (see the compile-time checks of the guide for "
                    + "the setting of each build tool), or compile without checks with " + ProcessorOptions.CHECKS + "=false"),
                    element);
            return;
        }
        Optional<SchemaModel> schema = current.schema.model();
        if (schema.isEmpty()) {
            current.failed = true;
            current.schema.problems().forEach(it -> report(context, it, null));
            return;
        }
        current.checked++;
        ElementTypeModel model = current.model;
        List<Finding> findings = ResolverClassFindings.check(schema.get(), model, model.of(element));
        current.findings += findings.size();
        for (Finding finding : findings) {
            MethodElement method = finding.member() == null ? null : model.elementOf(finding.member());
            report(context, finding, method != null && element.equals(method.getDeclaringType()) ? method : element);
        }
    }

    // a bean that is not a resolver can still declare a coordinate, which the startup rejects
    private static boolean hasCoordinateMethod(ClassElement element) {
        return element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance()).stream()
                .anyMatch(it -> it.hasStereotype(AnnotationNames.SCHEMA_COORDINATE));
    }

    private static boolean isResolver(ClassElement element) {
        return element.hasStereotype(AnnotationNames.ROOT_RESOLVER) || element.hasStereotype(AnnotationNames.TYPE_RESOLVER);
    }

    // the exceptions with which Micronaut postpones an element to the next round or reports a processing error
    private static boolean isControlFlow(RuntimeException e) {
        String name = e.getClass().getSimpleName();
        return name.equals("ProcessingException") || name.contains("Postpone");
    }

    private static void report(VisitorContext context, Finding finding, @Nullable Element element) {
        context.fail(FindingRenderer.render(finding), element);
    }

    /** The state of one compilation: the options, the schema, the model and the counts of the note. */
    private static final class Compilation {

        final ProcessorOptions options;
        final CheckedSchema schema;
        final ElementTypeModel model = new ElementTypeModel();
        boolean failed;
        boolean noted;
        int checked;
        int findings;

        Compilation(ProcessorOptions options) {
            this.options = options;
            this.schema = new CheckedSchema(options.schemaDirs());
        }
    }
}
