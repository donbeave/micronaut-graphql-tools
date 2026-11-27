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
package io.micronaut.graphql.tools.maven;

import io.micronaut.graphql.tools.codegen.Diagnostic;
import io.micronaut.graphql.tools.codegen.GeneratedFile;
import io.micronaut.graphql.tools.codegen.GenerationResult;
import io.micronaut.graphql.tools.codegen.GraphQLCodeGenerator;
import io.micronaut.graphql.tools.codegen.Options;
import io.micronaut.graphql.tools.codegen.OptionsResult;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The {@code generate} goal: generates Java, Kotlin or Groovy sources from the GraphQL schema files of a directory.
 *
 * <p>Every generator option is a parameter of the same name and default, and the parameter value is the string form of the option
 * ({@link Options#fromMap(Map)}): an enum is its constant name, a list is a list of child elements, a map is child elements named by
 * key. {@code schemaDirectory}, {@code outputDirectory} and {@code skip} belong to the plugin, as they are not options. The files are
 * written under {@code <outputDirectory>/<language>} ({@code java}, {@code kotlin} or {@code groovy}), and that directory is added to
 * the compile source roots of the project.</p>
 *
 * <p>The goal binds to {@code generate-sources} by default, but a third-party plugin cannot join the lifecycle mapping of a packaging,
 * so the project declares an {@code <execution>}.</p>
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public class GenerateMojo extends AbstractMojo {

    private static final String PROPERTY = "micronaut.graphql.tools.codegen.";
    private static final String NONE = "none";

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${plugin}", readonly = true, required = true)
    private PluginDescriptor plugin;

    /** Skips the goal. */
    @Parameter(property = PROPERTY + "skip", defaultValue = "false")
    private boolean skip;

    /** The directory that holds the schema files. */
    @Parameter(property = PROPERTY + "schema.directory", defaultValue = "${project.basedir}/src/main/resources/graphql")
    private File schemaDirectory;

    /** The directory that the generated sources are written under, in a subdirectory of the language. */
    @Parameter(property = PROPERTY + "output.directory", defaultValue = "${project.build.directory}/generated-sources/graphql-tools")
    private File outputDirectory;

    /** The language of the generated sources: {@code JAVA}, {@code KOTLIN} or {@code GROOVY}. */
    @Parameter(property = PROPERTY + "language", defaultValue = "JAVA")
    private String language;

    /** What is generated: {@code SERVER} or {@code CLIENT}. */
    @Parameter(property = PROPERTY + "mode", defaultValue = "SERVER")
    private String mode;

    /** The package of the generated sources. Required. */
    @Parameter(property = PROPERTY + "package.name")
    private String packageName;

    /** The package of the model types; empty means {@code <packageName>.model}. */
    @Parameter(property = PROPERTY + "model.package")
    private String modelPackage;

    /** The package of the resolver contracts; empty means {@code <packageName>.resolver}. */
    @Parameter(property = PROPERTY + "resolver.package")
    private String resolverPackage;

    /** The package of the name constants; empty means {@code <packageName>.names}. */
    @Parameter(property = PROPERTY + "names.package")
    private String namesPackage;

    /** The class name of the annotation that marks generated types; {@code none} for no annotation. */
    @Parameter(property = PROPERTY + "generated.annotation", defaultValue = Options.DEFAULT_GENERATED_ANNOTATION)
    private String generatedAnnotation;

    /** The shape of a model type: {@code RECORD} or {@code BEAN}. */
    @Parameter(property = PROPERTY + "model.style", defaultValue = "RECORD")
    private String modelStyle;

    /** The SDL names of the object types that are generated as interfaces. */
    @Parameter
    private List<String> interfaceTypes;

    /** A prefix for the name of every generated type. */
    @Parameter(property = PROPERTY + "type.name.prefix")
    private String typeNamePrefix;

    /** A suffix for the name of every generated type. */
    @Parameter(property = PROPERTY + "type.name.suffix")
    private String typeNameSuffix;

    /** The class name of the annotation on a nullable type; the default follows the mode, {@code none} for no annotation. */
    @Parameter(property = PROPERTY + "nullable.annotation")
    private String nullableAnnotation;

    /** Whether a resolver contract is generated for the root types. */
    @Parameter(property = PROPERTY + "root.contracts", defaultValue = "true")
    private boolean rootContracts;

    /** The name of a resolver contract, with the placeholders {@code {Field}} and {@code {Operation}}. */
    @Parameter(property = PROPERTY + "contract.name.template", defaultValue = Options.DEFAULT_CONTRACT_NAME_TEMPLATE)
    private String contractNameTemplate;

    /** Whether a Kotlin contract method is {@code suspend}. */
    @Parameter(property = PROPERTY + "kotlin.suspend", defaultValue = "false")
    private boolean kotlinSuspend;

    /** The class name of the type that a subscription method returns. */
    @Parameter(property = PROPERTY + "subscription.return.type", defaultValue = Options.DEFAULT_SUBSCRIPTION_RETURN_TYPE)
    private String subscriptionReturnType;

    /** The classes of custom scalars and of single fields, keyed by the SDL name or by {@code Type.field}. */
    @Parameter
    private Map<String, String> typeMappings;

    /** Which name constants are generated: {@code NONE}, {@code TYPES} or {@code FIELDS}. */
    @Parameter(property = PROPERTY + "name.constants", defaultValue = "FIELDS")
    private String nameConstants;

    /** The globs that select the schema files, relative to {@code schemaDirectory}. */
    @Parameter(property = PROPERTY + "includes", defaultValue = "**/*.graphqls,**/*.graphql")
    private List<String> includes;

    /** The globs that remove schema files from the selection. */
    @Parameter(property = PROPERTY + "excludes")
    private List<String> excludes;

    /** Whether {@code #} comments before a definition are its description; it must match the runtime setting. */
    @Parameter(property = PROPERTY + "comments.as.descriptions", defaultValue = "false")
    private boolean commentsAsDescriptions;

    /**
     * Creates the mojo. Maven instantiates it and sets the parameters.
     */
    public GenerateMojo() {
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("GraphQL code generation is skipped.");
            return;
        }
        Options options = options();
        Path schemaRoot = schemaDirectory.toPath();
        if (!schemaRoot.toFile().isDirectory()) {
            throw new MojoFailureException("The schema directory " + schemaRoot + " does not exist. Set schemaDirectory, or skip the goal.");
        }
        Path outputRoot = outputDirectory.toPath().toAbsolutePath().normalize();
        String languageDirectory = options.language().name().toLowerCase(Locale.ROOT);
        project.addCompileSourceRoot(outputRoot.resolve(languageDirectory).toString());

        List<Path> schemaFiles = SchemaFiles.select(schemaRoot, options.includes(), options.excludes());
        String fingerprint;
        try {
            fingerprint = GeneratedSources.fingerprint(generatorVersion(), options.toMap(), schemaRoot, schemaFiles);
        } catch (UncheckedIOException e) {
            String message = "[" + Diagnostic.SCHEMA_FILE_UNREADABLE + "] A schema file cannot be read: " + e.getCause().getMessage();
            getLog().error(message);
            throw new MojoFailureException(message, e);
        }
        if (GeneratedSources.isUpToDate(outputRoot, fingerprint)) {
            getLog().info("The generated GraphQL sources in " + outputRoot + " are up to date.");
            return;
        }
        if (schemaFiles.isEmpty()) {
            getLog().warn("No schema file in " + schemaRoot + " matches the includes " + options.includes() + ".");
            reconcile(outputRoot, fingerprint, Map.of());
            return;
        }

        GenerationResult result = GraphQLCodeGenerator.builder()
                .withSchemaFiles(schemaFiles)
                .withOutputDirectory(outputRoot.resolve(languageDirectory))
                .withOptions(options)
                .build()
                .generate();
        report(result.diagnostics());
        if (result.hasErrors()) {
            throw new MojoFailureException("The GraphQL code generation failed with " + result.diagnostics().stream().filter(Diagnostic::isError).count()
                    + " error(s); they are logged above.");
        }
        Map<String, String> files = new LinkedHashMap<>();
        for (GeneratedFile file : result.files()) {
            files.put(languageDirectory + "/" + file.path(), file.content());
        }
        reconcile(outputRoot, fingerprint, files);
    }

    private void reconcile(Path outputRoot, String fingerprint, Map<String, String> files) throws MojoExecutionException {
        try {
            GeneratedSources.Outcome outcome = GeneratedSources.reconcile(outputRoot, fingerprint, files);
            getLog().info("Generated GraphQL sources in " + outputRoot + ": " + outcome.written() + " written, "
                    + outcome.unchanged() + " unchanged, " + outcome.deleted() + " deleted.");
        } catch (IOException | RuntimeException e) {
            throw new MojoExecutionException("The generated sources cannot be written under " + outputRoot + ": " + e.getMessage(), e);
        }
    }

    /**
     * The options of the configuration, read through their string form. An option that the configuration leaves out has its default.
     *
     * @return the options
     * @throws MojoFailureException when a value is not valid; the diagnostics are logged
     */
    Options options() throws MojoFailureException {
        OptionsResult result = Options.fromMap(optionMap());
        report(result.diagnostics());
        if (result.hasErrors()) {
            throw new MojoFailureException("The configuration of the goal is invalid; the options are logged above.");
        }
        return result.options();
    }

    /**
     * The string form of the option parameters; a parameter that is not set is absent, so the option keeps its default.
     *
     * @return the string form, as {@link Options#fromMap(Map)} reads it
     */
    Map<String, String> optionMap() {
        Map<String, String> map = new LinkedHashMap<>();
        put(map, "language", language);
        put(map, "mode", mode);
        put(map, "packageName", packageName);
        put(map, "modelPackage", modelPackage);
        put(map, "resolverPackage", resolverPackage);
        put(map, "namesPackage", namesPackage);
        putClassName(map, "generatedAnnotation", generatedAnnotation);
        put(map, "modelStyle", modelStyle);
        putList(map, "interfaceTypes", interfaceTypes);
        put(map, "typeNamePrefix", typeNamePrefix);
        put(map, "typeNameSuffix", typeNameSuffix);
        putClassName(map, "nullableAnnotation", nullableAnnotation);
        map.put("rootContracts", Boolean.toString(rootContracts));
        put(map, "contractNameTemplate", contractNameTemplate);
        map.put("kotlinSuspend", Boolean.toString(kotlinSuspend));
        put(map, "subscriptionReturnType", subscriptionReturnType);
        if (typeMappings != null) {
            typeMappings.forEach((key, value) -> map.put("typeMappings." + key, value));
        }
        put(map, "nameConstants", nameConstants);
        putList(map, "includes", includes);
        putList(map, "excludes", excludes);
        map.put("commentsAsDescriptions", Boolean.toString(commentsAsDescriptions));
        return map;
    }

    private String generatorVersion() {
        return plugin != null && plugin.getVersion() != null ? plugin.getVersion() : "unknown";
    }

    private void report(List<Diagnostic> diagnostics) {
        for (Diagnostic diagnostic : diagnostics) {
            String origin = diagnostic.origin() != null ? diagnostic.origin().path() + ":" + diagnostic.origin().line() + " " : "";
            String message = "[" + diagnostic.code() + "] " + origin + diagnostic.message();
            if (diagnostic.isError()) {
                getLog().error(message);
            } else {
                getLog().warn(message);
            }
        }
    }

    private static void put(Map<String, String> map, String key, String value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    /**
     * The options that name a class use the empty string for none, but Maven reads an empty element as an absent value, which has the
     * default. So {@code none} stands for the empty string; it is not a class name that the generator could use.
     */
    private static void putClassName(Map<String, String> map, String key, String value) {
        if (value != null) {
            map.put(key, NONE.equals(value.trim()) ? "" : value);
        }
    }

    private static void putList(Map<String, String> map, String key, List<String> values) {
        if (values != null) {
            map.put(key + ".size", Integer.toString(values.size()));
            for (int i = 0; i < values.size(); i++) {
                map.put(key + "." + i, values.get(i));
            }
        }
    }
}
