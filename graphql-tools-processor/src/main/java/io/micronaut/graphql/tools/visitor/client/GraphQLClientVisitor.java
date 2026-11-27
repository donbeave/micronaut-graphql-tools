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
package io.micronaut.graphql.tools.visitor.client;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.binding.internal.client.ClientDirectories;
import io.micronaut.graphql.tools.binding.internal.client.ClientOperation;
import io.micronaut.graphql.tools.binding.internal.client.ClientOperations;
import io.micronaut.graphql.tools.binding.internal.client.ClientOutcome;
import io.micronaut.graphql.tools.binding.internal.client.ClientRequest;
import io.micronaut.graphql.tools.binding.internal.client.ClientRules;
import io.micronaut.graphql.tools.binding.internal.client.method.ClientScalars;
import io.micronaut.graphql.tools.binding.internal.client.ClientSchemaBuilder;
import io.micronaut.graphql.tools.binding.internal.client.method.ClientVariableRules;
import io.micronaut.graphql.tools.binding.internal.finding.Finding;
import io.micronaut.graphql.tools.binding.internal.finding.FindingRenderer;
import io.micronaut.graphql.tools.visitor.ClientElements;
import io.micronaut.graphql.tools.visitor.ModuleOwnership;
import io.micronaut.graphql.tools.visitor.ProcessorOptions;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.TypeElementQuery;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The compile-time checks of the declarative client, as a visitor that runs under javac, KSP, kapt and the Groovy compiler. For
 * each method of a {@code @GraphQLClient} type that carries {@code @GraphQLOperation} it takes the document (inline, or an
 * operation of the client directory), assembles the operation with the fragments it uses, validates the result against the
 * client schema and writes it, with the operation name and type, the variables and the payload mode, into the annotation
 * metadata of the method as {@code GraphQLOperationMetadata}, so that the runtime never parses GraphQL. It writes no source,
 * resource or service file.
 *
 * <p>It is isolating, like the resolver visitor: the verdict for an interface depends on the interface and on the client
 * directories, which a build declares as an input of the compile task. The state lives in one {@link Compilation} per
 * {@link #start}, nothing is static, and the options are the ones that {@link ProcessorOptions} reads and reports once for both
 * visitors. With {@code client.checks=false} (or {@code checks=false}) the documents are still parsed and assembled, and one
 * note says that nothing was validated.</p>
 */
public final class GraphQLClientVisitor implements TypeElementVisitor<Object, Object> {

    private static final String TAG = "[graphql-tools] ";

    private static final String CLIENT = "io.micronaut.graphql.tools.client.annotation.GraphQLClient";

    private static final String OPERATION = "io.micronaut.graphql.tools.client.annotation.GraphQLOperation";

    private static final String METADATA = "io.micronaut.graphql.tools.client.internal.GraphQLOperationMetadata";

    private static final String GET = "GET";

    private static final String MUTATION = "mutation";

    private static final String RETRYABLE = "io.micronaut.retry.annotation.Retryable";

    @Nullable
    private Compilation compilation;

    // after the resolver visitor (-95) and Micronaut Validation's (-90); nothing here depends on the others
    @Override
    public int getOrder() {
        return -96;
    }

    @Override
    public @NonNull Set<String> getSupportedAnnotationNames() {
        return Set.of(CLIENT, OPERATION);
    }

    @Override
    public @NonNull Set<String> getSupportedOptions() {
        return Set.of(ProcessorOptions.CLIENT_DIRS, ProcessorOptions.CLIENT_CHECKS);
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
        ProcessorOptions options = ProcessorOptions.read(context.getOptions());
        // the resolver visitor reports a wrong option once; with one, no operation is checked
        compilation = options.errors().isEmpty() ? new Compilation(options, context) : null;
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        Compilation current = compilation;
        if (current == null || current.failed || !element.hasStereotype(CLIENT) || element.isEnum()) {
            return;
        }
        List<String> mutations = new ArrayList<>();
        // the custom scalars that this client has mapped so far: one client reads a scalar as one Java type
        ClientScalars scalars = new ClientScalars();
        for (MethodElement method : element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared())) {
            if (method.hasDeclaredAnnotation(OPERATION) && visitOperation(current, element, method, scalars, context)) {
                mutations.add(member(method));
            }
        }
        if (!mutations.isEmpty() && element.hasStereotype(RETRYABLE)) {
            current.findings++;
            context.fail(FindingRenderer.render(ClientRules.retryOnMutation(element.getName(), mutations)), element);
        }
    }

    // whether the method is an operation that was resolved and is a mutation
    private boolean visitOperation(Compilation current, ClassElement client, MethodElement method, ClientScalars scalars,
                                   VisitorContext context) {
        try {
            ClientRequest request = new ClientRequest(member(method), method.getName(),
                    client.getAnnotationMetadata().stringValue(CLIENT, "schema").orElse(""),
                    method.getAnnotationMetadata().stringValue(OPERATION, "value").orElse(""),
                    method.getAnnotationMetadata().stringValue(OPERATION, "name").orElse(""),
                    GET.equals(method.getAnnotationMetadata().stringValue(OPERATION, "method").orElse("")));
            ClientOutcome outcome = current.operations().resolve(request);
            for (Finding finding : outcome.findings()) {
                current.findings++;
                context.fail(FindingRenderer.render(finding), method);
            }
            ClientOperation operation = outcome.operation();
            if (operation != null && outcome.findings().isEmpty()) {
                List<Finding> rules = ClientVariableRules.check(current.elements.method(method, request.member()), operation,
                        current.elements.model(), scalars);
                for (Finding finding : rules) {
                    current.findings++;
                    context.fail(FindingRenderer.render(finding), method);
                }
                if (rules.isEmpty()) {
                    write(method, operation);
                }
            }
            // a mutation that fails for another reason still counts for the retry rule: its type was selected
            return MUTATION.equals(outcome.selectedType());
        } catch (RuntimeException e) {
            if (isControlFlow(e)) {
                throw e;
            }
            StringWriter trace = new StringWriter();
            e.printStackTrace(new PrintWriter(trace));
            current.failed = true;
            context.fail(FindingRenderer.render(new Finding(BindingCode.PROCESSOR_INTERNAL_ERROR.getCode(), member(method),
                    "the compile-time check failed on " + member(method) + ": " + e + "\n" + trace, null, null, null,
                    "report this; every operation is parsed and assembled whatever " + ProcessorOptions.CLIENT_CHECKS + " says, so no option "
                    + "skips the work that failed")), method);
        }
        return false;
    }

    private static void write(MethodElement method, ClientOperation operation) {
        method.annotate(METADATA, builder -> {
            builder.member("document", operation.document());
            builder.member("operationName", operation.operationName());
            builder.member("operationType", operation.operationType());
            builder.member("variables", operation.variables().toArray(new String[0]));
            builder.member("payload", operation.payload().token());
        });
    }

    // binary.Name#method(parameter types), as the resolver findings name a member
    private static String member(MethodElement method) {
        StringBuilder text = new StringBuilder(method.getDeclaringType().getName()).append('#').append(method.getName()).append('(');
        ParameterElement[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            ClassElement type = parameters[i].getType();
            text.append(i == 0 ? "" : ",").append(type.getName()).append("[]".repeat(type.getArrayDimensions()));
        }
        return text.append(')').toString();
    }

    // the exceptions with which Micronaut postpones an element to the next round or reports a processing error
    private static boolean isControlFlow(RuntimeException e) {
        String name = e.getClass().getSimpleName();
        return name.equals("ProcessingException") || name.contains("Postpone");
    }

    @Override
    public void finish(VisitorContext context) {
        Compilation current = compilation;
        ClientOperations operations = current == null ? null : current.built;
        if (current == null || operations == null || current.noted || operations.operations() == 0) {
            return;
        }
        if (!current.checks) {
            current.noted = true;
            context.info(TAG + "GraphQL client checks are off (" + (current.options.checks() ? ProcessorOptions.CLIENT_CHECKS
                    : ProcessorOptions.CHECKS) + "=false): " + operations.operations() + " client "
                    + (operations.operations() == 1 ? "operation was" : "operations were") + " parsed and assembled, and not "
                    + "validated against a schema.");
        } else if (!current.failed && current.findings == 0) {
            current.noted = true;
            context.info(TAG + "checked " + operations.operations() + " client "
                    + (operations.operations() == 1 ? "operation" : "operations") + " against " + operations.schemasBuilt()
                    + " client " + (operations.schemasBuilt() == 1 ? "schema" : "schemas") + ".");
        }
    }

    /** The state of one compilation: the options and the operations of the client directories. */
    private static final class Compilation {

        final ProcessorOptions options;
        final boolean checks;
        final VisitorContext context;
        final ClientElements elements = new ClientElements();
        // built on the first operation: a compilation with no client reads no directory
        @Nullable
        ClientOperations built;
        boolean failed;
        boolean noted;
        int findings;

        Compilation(ProcessorOptions options, VisitorContext context) {
            this.options = options;
            this.checks = options.checks() && options.clientChecks();
            this.context = context;
        }

        ClientOperations operations() {
            if (built == null) {
                ModuleOwnership.Ownership ownership = ModuleOwnership.of(options.moduleDir(), context);
                String foreign = ownership.foreignOutput() == null ? null : "the client directories and the schema directories belong "
                        + "to the module in " + ownership.moduleDir() + ", an earlier compilation of the same JVM, and this compilation "
                        + "writes to " + ownership.foreignOutput();
                built = new ClientOperations(new ClientDirectories(options.clientDirs(), options.schemaDirs(), foreign), checks,
                        ClientSchemaBuilder.STACK_BYTES);
            }
            return built;
        }
    }
}
