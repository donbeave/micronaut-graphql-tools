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
package io.micronaut.graphql.tools.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * The plugin {@code io.micronaut.graphql.tools}. It adds the {@code graphqlTools} extension, the configurations that resolve the
 * generator (the bucket {@value #CODEGEN_CONFIGURATION} and the resolvable {@value #CODEGEN_CLASSPATH_CONFIGURATION}), one
 * {@link GenerateGraphQLTask} per {@link CodegenSpec}, and the wiring of each task's output into the main source set of the
 * language the spec generates.
 */
public class GraphQLToolsPlugin implements Plugin<Project> {

    /** The id of the plugin. */
    public static final String PLUGIN_ID = "io.micronaut.graphql.tools";

    /** The name of the extension. */
    public static final String EXTENSION_NAME = "graphqlTools";

    /** The name of the bucket configuration that holds the generator dependency. */
    public static final String CODEGEN_CONFIGURATION = "graphqlToolsCodegen";

    /** The name of the resolvable configuration that the generation tasks read the generator classpath from. */
    public static final String CODEGEN_CLASSPATH_CONFIGURATION = "graphqlToolsCodegenClasspath";

    /** The name of the resolvable configuration that holds the jars of the project's dependencies, whose type mappings the generator reads. */
    public static final String DEPENDENCY_CLASSPATH_CONFIGURATION = "graphqlToolsDependencyClasspath";

    private static final String JAVA = "JAVA";
    private static final String KOTLIN = "KOTLIN";
    private static final String GROOVY = "GROOVY";
    private static final String SERVER = "SERVER";
    private static final String CLIENT = "CLIENT";
    private static final String GROUP = "io.micronaut.graphql.tools";
    private static final String CODEGEN_ARTIFACT = "micronaut-graphql-tools-codegen";
    private static final String RUNTIME_ARTIFACT = "micronaut-graphql-tools";
    private static final String CLIENT_ARTIFACT = "micronaut-graphql-tools-client";

    @Override
    public void apply(Project project) {
        GraphQLToolsExtension extension = project.getExtensions().create(EXTENSION_NAME, GraphQLToolsExtension.class);
        extension.getVersion().convention(pluginVersion());
        extension.getAddRuntimeDependency().convention(true);
        extension.getSchemaDirs().convention(project.getLayout().getProjectDirectory().dir("src/main/resources/graphql"));

        Configuration bucket = project.getConfigurations().dependencyScope(CODEGEN_CONFIGURATION, c ->
                c.setDescription("The dependencies of the GraphQL code generator, which runs in an isolated worker.")).get();
        project.getConfigurations().resolvable(CODEGEN_CLASSPATH_CONFIGURATION, c -> {
            c.setDescription("The class path of the GraphQL code generator.");
            c.extendsFrom(bucket);
            c.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
            c.getAttributes().attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
            c.getAttributes().attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                    project.getObjects().named(LibraryElements.class, LibraryElements.JAR));
        });
        project.getConfigurations().resolvable(DEPENDENCY_CLASSPATH_CONFIGURATION, c ->
                c.setDescription("The dependencies of the project, as jars, whose type mappings the GraphQL code generator reads."));
        project.getDependencies().addProvider(CODEGEN_CONFIGURATION, extension.getVersion().map(v -> coordinate(CODEGEN_ARTIFACT, v)));

        extension.getCodegenSpecs().all(spec -> configure(project, extension, spec));
        CompileTimeWiring.wire(project, extension);

        project.getPluginManager().withPlugin("java", plugin -> {
            configureDependencyClasspath(project);
            extension.getCodegenSpecs().all(spec -> addRuntimeDependency(project, extension, spec));
            extension.getCodegenSpecs().all(spec -> wireResources(project, spec));
            wire(project, extension, JAVA, mainSourceSet(project).getJava());
        });
        project.getPluginManager().withPlugin("groovy", plugin -> wire(project, extension, GROOVY, extensionOf(project, "groovy")));
        project.getPluginManager().withPlugin("org.jetbrains.kotlin.jvm", plugin -> wire(project, extension, KOTLIN, extensionOf(project, "kotlin")));
    }

    private static void configure(Project project, GraphQLToolsExtension extension, CodegenSpec spec) {
        spec.getLanguage().convention(project.provider(() -> inferLanguage(project, spec)));
        spec.getSchemaDirs().convention(extension.getSchemaDirs());
        spec.getOutputDirectory().convention(project.getLayout().getBuildDirectory().dir("generated/sources/graphqlTools/" + spec.getName()));
        spec.getResourcesDirectory().convention(project.getLayout().getBuildDirectory().dir("generated/resources/graphqlTools/" + spec.getName()));
        project.getTasks().register(taskName(spec.getName()), GenerateGraphQLTask.class, task -> {
            task.setDescription("Generates sources from the GraphQL schema files of the spec '" + spec.getName() + "'.");
            task.setGroup("build");
            task.getSchemaDirectories().from(spec.getSchemaDirs());
            task.getOptions().set(project.provider(spec::toOptionMap));
            task.getGeneratorClasspath().from(project.getConfigurations().named(CODEGEN_CLASSPATH_CONFIGURATION));
            task.getDependencyClasspath().from(spec.getTypeMappingsFromDependencies().orElse(true).map(read ->
                    read ? (FileCollection) project.getConfigurations().getByName(DEPENDENCY_CLASSPATH_CONFIGURATION) : project.files()));
            task.getOutputDirectory().set(spec.getOutputDirectory());
            task.getResourcesDirectory().set(spec.getResourcesDirectory());
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void configureDependencyClasspath(Project project) {
        Configuration compile = project.getConfigurations().getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME);
        project.getConfigurations().named(DEPENDENCY_CLASSPATH_CONFIGURATION, c -> {
            c.extendsFrom(project.getConfigurations().getByName(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME),
                    project.getConfigurations().getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME));
            for (Attribute attribute : compile.getAttributes().keySet()) {
                c.getAttributes().attribute(attribute, compile.getAttributes().getAttribute(attribute));
            }
            c.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
            c.getAttributes().attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                    project.getObjects().named(LibraryElements.class, LibraryElements.JAR));
        });
    }

    private static void wireResources(Project project, CodegenSpec spec) {
        TaskProvider<?> task = project.getTasks().named(taskName(spec.getName()));
        mainSourceSet(project).getResources().srcDir(project.files(spec.getResourcesDirectory()).builtBy(task));
    }

    private static String inferLanguage(Project project, CodegenSpec spec) {
        if (CLIENT.equals(spec.getMode().getOrElse(SERVER))) {
            return JAVA;
        }
        if (project.getPluginManager().hasPlugin("org.jetbrains.kotlin.jvm")) {
            return KOTLIN;
        }
        if (project.getPluginManager().hasPlugin("groovy")) {
            return GROOVY;
        }
        return JAVA;
    }

    static String taskName(String specName) {
        if (CodegenSpec.MAIN.equals(specName)) {
            return "generateGraphQL";
        }
        return "generate" + specName.substring(0, 1).toUpperCase(Locale.ROOT) + specName.substring(1) + "GraphQL";
    }

    private static void addRuntimeDependency(Project project, GraphQLToolsExtension extension, CodegenSpec spec) {
        Provider<String> notation = extension.getAddRuntimeDependency().flatMap(add -> {
            if (!add) {
                return project.getProviders().provider(() -> null);
            }
            return spec.getMode().orElse(SERVER).zip(extension.getVersion(), (mode, version) ->
                    coordinate(CLIENT.equals(mode) ? CLIENT_ARTIFACT : RUNTIME_ARTIFACT, version));
        });
        project.getDependencies().addProvider("implementation", notation);
    }

    private static String coordinate(String artifact, String version) {
        return GROUP + ":" + artifact + ":" + version;
    }

    private static SourceSet mainSourceSet(Project project) {
        return project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
    }

    private static SourceDirectorySet extensionOf(Project project, String name) {
        return mainSourceSet(project).getExtensions().findByName(name) instanceof SourceDirectorySet set ? set : null;
    }

    private static void wire(Project project, GraphQLToolsExtension extension, String language, SourceDirectorySet target) {
        if (target == null) {
            return;
        }
        extension.getCodegenSpecs().all(spec -> {
            TaskProvider<?> task = project.getTasks().named(taskName(spec.getName()));
            Provider<Object> selected = spec.getLanguage().map(value ->
                    language.equals(value) ? (Object) spec.getOutputDirectory().get().getAsFile() : List.of());
            target.srcDir(project.files(selected).builtBy(task));
        });
    }

    private static String pluginVersion() {
        Properties properties = new Properties();
        try (InputStream in = GraphQLToolsPlugin.class.getResourceAsStream("plugin-version.properties")) {
            if (in == null) {
                throw new IllegalStateException("The plugin jar has no plugin-version.properties");
            }
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return properties.getProperty("version");
    }
}
