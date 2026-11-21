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

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.binding.internal.finding.Finding;
import io.micronaut.graphql.tools.binding.internal.finding.FindingRenderer;
import io.micronaut.graphql.tools.binding.internal.finding.Severity;
import io.micronaut.graphql.tools.binding.internal.model.AnnotationNames;
import io.micronaut.graphql.tools.binding.internal.rules.ResolverClassFindings;
import io.micronaut.graphql.tools.binding.internal.rules.TypeClassDerivation;
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
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * look up a class by name, does not ask for the project directory, reads the class output only to compare it with
 * {@code module.dir} (see {@link ModuleOwnership}), and writes no file.</p>
 */
public final class GraphQLToolsVisitor implements TypeElementVisitor<Object, Object> {

    private static final String TAG = "[graphql-tools] ";

    // The finding that a check of one class cannot prove: another class can make a class introspected with
    // @Introspected(classes) or (packages), and so can a dependency, and an isolating visitor sees neither. An error here
    // would be a compile error where the startup passes, so it is a warning, and the startup decides.
    private static final String UNPROVEN = BindingCode.CLASS_NOT_INTROSPECTED.code();

    private static final String INTROSPECTED = "io.micronaut.core.annotation.Introspected";

    @Nullable
    private Compilation compilation;

    // Micronaut runs the visitors from the highest order to the lowest: this one runs after Micronaut Validation's visitor (-90),
    // which rewrites @Introspected, and before the one that generates the introspections (-100)
    @Override
    public int getOrder() {
        return -95;
    }

    @Override
    public @NonNull Set<String> getSupportedAnnotationNames() {
        return Set.of(AnnotationNames.ROOT_RESOLVER, AnnotationNames.TYPE_RESOLVER, AnnotationNames.SCHEMA_COORDINATE,
                AnnotationNames.MODELS, AnnotationNames.TYPE, INTROSPECTED);
    }

    @Override
    public @NonNull Set<String> getSupportedOptions() {
        return ProcessorOptions.ADVERTISED;
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
        started.foreignOutput = ModuleOwnership.foreignOutput(started.options.moduleDir(), context);
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
        // the metadata is written whatever the checks are: the startup reads it, and it is not a check
        if (ResolverContractsWriter.applies(element)) {
            ResolverContractsWriter.write(element);
        }
        Compilation current = compilation;
        if (element.hasDeclaredAnnotation(AnnotationNames.MODELS)) {
            introspectModels(element);
            if (current != null) {
                recordModels(current, element, context);
            }
        }
        if (current != null && current.options.checks()) {
            recordTypeClass(current, element);
        }
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

    // a class that an object type can match by @GraphQLType(name) or the naming convention; only the names are kept, because
    // an element of an earlier round is not valid any more under KSP
    private static void recordTypeClass(Compilation current, ClassElement element) {
        if (element.isInterface() || element.isAbstract() || !element.hasStereotype(INTROSPECTED)) {
            return;
        }
        String declared = element.getAnnotationMetadata().stringValue(AnnotationNames.TYPE, "name").orElse("");
        current.typeClasses.put(element.getName(),
                new TypeClassDerivation.Candidate(element.getName(), element.getSimpleName(), declared));
    }

    // Another processor can re-annotate the class with @Introspected after Micronaut resolved @GraphQLModels into it and keep only
    // its own members (Micronaut Validation's processor does), so the listed classes and packages are put back on the
    // declared @Introspected here, together with what it carries already. This runs after the visitors that annotate before it
    // (see getOrder); the members are set, not added, so a class listed twice is introspected once.
    private static void introspectModels(ClassElement element) {
        var models = element.getAnnotationMetadata().findAnnotation(AnnotationNames.MODELS).orElse(null);
        if (models == null) {
            return;
        }
        var introspected = element.getAnnotationMetadata().findDeclaredAnnotation(INTROSPECTED).orElse(null);
        element.annotate(INTROSPECTED, builder -> {
            for (String member : List.of("classes", "includedAnnotations")) {
                AnnotationClassValue<?>[] listed = union(models.annotationClassValues(member),
                        introspected == null ? new AnnotationClassValue<?>[0] : introspected.annotationClassValues(member));
                if (listed.length > 0) {
                    builder.member(member, listed);
                }
            }
            String[] packages = java.util.stream.Stream.concat(Arrays.stream(models.stringValues("packages")),
                    introspected == null ? java.util.stream.Stream.<String>empty() : Arrays.stream(introspected.stringValues("packages")))
                    .distinct().toArray(String[]::new);
            if (packages.length > 0) {
                builder.member("packages", packages);
            }
            if (introspected != null) {
                var indexed = introspected.getAnnotations("indexed");
                if (!indexed.isEmpty()) {
                    builder.member("indexed", indexed.toArray(io.micronaut.core.annotation.AnnotationValue[]::new));
                }
            }
        });
    }

    private static AnnotationClassValue<?>[] union(AnnotationClassValue<?>[] first, AnnotationClassValue<?>[] second) {
        Map<String, AnnotationClassValue<?>> byName = new LinkedHashMap<>();
        for (AnnotationClassValue<?> value : first) {
            byName.putIfAbsent(value.getName(), value);
        }
        for (AnnotationClassValue<?> value : second) {
            byName.putIfAbsent(value.getName(), value);
        }
        return byName.values().toArray(new AnnotationClassValue<?>[0]);
    }

    // the classes that @GraphQLModels lists make the compile-time check see them as introspected; an annotation that lists
    // nothing generates nothing, which is most likely a mistake
    private static void recordModels(Compilation current, ClassElement element, VisitorContext context) {
        var annotation = element.getAnnotationMetadata().findAnnotation(AnnotationNames.MODELS).orElse(null);
        if (annotation == null) {
            return;
        }
        List<String> classes = Arrays.stream(annotation.annotationClassValues("classes")).map(AnnotationClassValue::getName).toList();
        List<String> packages = List.of(annotation.stringValues("packages"));
        if (classes.isEmpty() && packages.isEmpty()) {
            if (!current.options.checks()) {
                return;
            }
            context.warn(TAG + "@GraphQLModels on " + element.getName() + " lists no class and no package, "
                    + "so it makes no class introspected; list the classes, or the packages with includedAnnotations", element);
            return;
        }
        if (!classes.isEmpty() && !packages.isEmpty() && current.options.checks()) {
            context.warn(TAG + "@GraphQLModels on " + element.getName() + " lists classes and packages, and Micronaut's "
                    + "@Introspected generates only one of the two for one annotation; use one annotation for the classes "
                    + "and another for the packages", element);
        }
        // the classes the annotation lists, and the classes of its packages that carry one of the annotations it names
        List<String> markers = Arrays.stream(annotation.annotationClassValues("includedAnnotations"))
                .map(AnnotationClassValue::getName).toList();
        current.coverage.add(classes, packages, markers);
    }

    @Override
    public void finish(VisitorContext context) {
        Compilation current = compilation;
        if (current == null) {
            return;
        }
        // the startup stops at the first class of a resolver that nothing introspects, so a class that a later
        // @GraphQLModels covers gives the next one its turn, and only the first class left over is reported
        Set<Integer> reported = new HashSet<>();
        for (Pending pending : current.pending) {
            if (pending.missed == null || !current.coverage.covers(pending.missed) && reported.add(pending.group)) {
                current.warnings++;
                // no element: a class element of an earlier round is not valid any more under KSP
                context.warn(FindingRenderer.render(unproven(pending.finding)), null);
            }
        }
        current.pending.clear();
        checkTypeClasses(current, context);
        if (current.noted) {
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
                    + (current.findings == 1 ? "finding" : "findings")
                    + (current.warnings == 0 ? "" : ", " + current.warnings + (current.warnings == 1 ? " warning" : " warnings")) + ".");
        }
    }

    // two classes of this compilation that match one object type of a union or interface: the startup fails on them too,
    // unless the dictionary maps the type, which a visitor cannot see
    private static void checkTypeClasses(Compilation current, VisitorContext context) {
        if (!current.options.checks() || current.failed || current.typeClasses.isEmpty()
                || current.options.schemaDirs().isEmpty() || !current.options.errors().isEmpty()) {
            return;
        }
        Optional<SchemaModel> schema = current.schema.model();
        if (schema.isEmpty()) {
            return;
        }
        List<Finding> ambiguities = TypeClassDerivation.ambiguities(schema.get().registry(),
                current.options.typeClassConvention(), current.typeClasses.values());
        for (Finding finding : ambiguities) {
            if (!current.reportedTypes.add(finding.coordinate() + finding.sentence())) {
                continue;
            }
            current.findings++;
            context.fail(FindingRenderer.render(finding), null);
        }
    }

    private void check(Compilation current, ClassElement element, VisitorContext context) {
        if (current.foreignOutput != null) {
            current.failed = true;
            report(context, new Finding(BindingCode.PROCESSOR_SCHEMA_NOT_CONFIGURED.code(), null,
                    "the schema directory is not configured for this module: " + ProcessorOptions.SCHEMA_DIRS + " and "
                    + ProcessorOptions.MODULE_DIR + " belong to the module in " + current.options.moduleDir()
                    + ", an earlier compilation of the same JVM, and this compilation writes to " + current.foreignOutput,
                    null, null, null, "set " + ProcessorOptions.SCHEMA_DIRS + " and " + ProcessorOptions.MODULE_DIR
                    + " in this module (see the compile-time checks of the guide for the setting of each build tool); if they are set and "
                    + "this build writes the output outside the project directory of the module, set " + ProcessorOptions.MODULE_DIR
                    + " to a directory that contains the output; or compile without checks with " + ProcessorOptions.CHECKS + "=false"),
                    element);
            return;
        }
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
        current.coverage.resetMissed();
        List<Finding> findings = ResolverClassFindings.check(schema.get(), model, model.of(element),
                current.options.accessorPrefixes());
        for (Finding finding : findings) {
            if (UNPROVEN.equals(finding.code())) {
                // a @GraphQLModels of this compilation that is visited after this class can still cover the class, so the
                // warning is made when the compilation finishes; the walk stopped at the class it names
                current.pending.add(new Pending(current.checked, finding, current.coverage.missedIn(finding.sentence())));
            } else {
                MethodElement method = finding.member() == null ? null : model.elementOf(finding.member());
                Element at = method != null && element.equals(method.getDeclaringType()) ? method : element;
                if (BindingCode.KOTLIN_NULLABILITY_LOOSE.code().equals(finding.code())
                        && current.options.looseNullability() != Severity.FAIL) {
                    // stricter than the schema: the code cannot fail for it, so it is off, or a warning, unless the build asks
                    if (current.options.looseNullability() == Severity.WARN) {
                        current.warnings++;
                        context.warn(FindingRenderer.render(finding), at);
                    }
                } else {
                    current.findings++;
                    report(context, finding, at);
                }
            }
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

    private static Finding unproven(Finding finding) {
        String sentence = finding.sentence().strip();
        if (sentence.endsWith(".")) {
            sentence = sentence.substring(0, sentence.length() - 1);
        }
        return new Finding(finding.code(), finding.coordinate(), sentence + " here; a class can also be made introspected by "
                + "@Introspected(classes) or @Introspected(packages) on another class, which a compile-time check of one class "
                + "cannot see, so this is a warning and the startup decides", finding.member(), finding.declaredIn(),
                finding.schemaLocation(), finding.fix());
    }

    private static void report(VisitorContext context, Finding finding, @Nullable Element element) {
        context.fail(FindingRenderer.render(finding), element);
    }

    private record Pending(int group, Finding finding, @Nullable ModelCoverage.Missed missed) {
    }

    /** The state of one compilation: the options, the schema, the model and the counts of the note. */
    private static final class Compilation {

        final ProcessorOptions options;
        final CheckedSchema schema;
        final ModelCoverage coverage = new ModelCoverage();
        final Map<String, TypeClassDerivation.Candidate> typeClasses = new LinkedHashMap<>();
        final Set<String> reportedTypes = new HashSet<>();
        final ElementTypeModel model = new ElementTypeModel(coverage);
        // the warnings that a @GraphQLModels visited later can still remove
        final List<Pending> pending = new ArrayList<>();
        @Nullable Path foreignOutput;
        boolean failed;
        boolean noted;
        int checked;
        int findings;
        int warnings;

        Compilation(ProcessorOptions options) {
            this.options = options;
            this.schema = new CheckedSchema(options.schemaDirs());
        }
    }
}
