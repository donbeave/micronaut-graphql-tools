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

import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileTree;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One code generation: the options of the generator, the schema directories and the output directory. Every option has the name
 * of the component of the generator's {@code Options} record; an option that is not set has the default of the generator, and so does a list option that is empty. An enum
 * option takes the constant name, for example {@code "KOTLIN"}.
 */
public abstract class CodegenSpec implements Named {

    /** The name of the spec that {@code codegen { }} configures. */
    public static final String MAIN = "main";

    private static final List<String> DEFAULT_INCLUDES = List.of("**/*.graphqls", "**/*.graphql");

    private final String name;

    /**
     * Creates the spec.
     *
     * @param name the name of the spec
     */
    @Inject
    public CodegenSpec(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * The directories that hold the schema files of this spec. The default is {@code graphqlTools.schemaDirs}.
     *
     * @return the directories
     */
    public abstract ConfigurableFileCollection getSchemaDirs();

    /**
     * The directory that the task writes the generated sources to, and that is added to the main source set. The task recreates it
     * before every run. The default is {@code build/generated/sources/graphqlTools/<name>}.
     *
     * @return the directory
     */
    public abstract DirectoryProperty getOutputDirectory();

    /**
     * The generated language: {@code JAVA}, {@code KOTLIN} or {@code GROOVY}. The default follows the applied Kotlin or Groovy
     * plugin, else {@code JAVA}.
     *
     * @return the property
     */
    public abstract Property<String> getLanguage();

    /**
     * {@code SERVER} (the default) or {@code CLIENT}.
     *
     * @return the property
     */
    public abstract Property<String> getMode();

    /**
     * The Java package of the generated sources. Required: the generator reports an error when it is empty.
     *
     * @return the property
     */
    public abstract Property<String> getPackageName();

    /**
     * The package of the models; empty derives it from {@code packageName}.
     *
     * @return the property
     */
    public abstract Property<String> getModelPackage();

    /**
     * The package of the resolver contracts; empty derives it from {@code packageName}.
     *
     * @return the property
     */
    public abstract Property<String> getResolverPackage();

    /**
     * The package of the name constants; empty derives it from {@code packageName}.
     *
     * @return the property
     */
    public abstract Property<String> getNamesPackage();

    /**
     * The class name of the {@code @Generated} annotation; empty for none.
     *
     * @return the property
     */
    public abstract Property<String> getGeneratedAnnotation();

    /**
     * {@code RECORD} (the default) or {@code BEAN}.
     *
     * @return the property
     */
    public abstract Property<String> getModelStyle();

    /**
     * The SDL names of the object types that are generated as interfaces.
     *
     * @return the property
     */
    public abstract ListProperty<String> getInterfaceTypes();

    /**
     * The prefix of every generated type name.
     *
     * @return the property
     */
    public abstract Property<String> getTypeNamePrefix();

    /**
     * The suffix of every generated type name.
     *
     * @return the property
     */
    public abstract Property<String> getTypeNameSuffix();

    /**
     * The class name of the annotation on a nullable element; empty for none.
     *
     * @return the property
     */
    public abstract Property<String> getNullableAnnotation();

    /**
     * Whether a resolver contract is generated for the root types. The default is {@code true}.
     *
     * @return the property
     */
    public abstract Property<Boolean> getRootContracts();

    /**
     * The name of a resolver contract, with placeholders; see the generator's {@code Options}.
     *
     * @return the property
     */
    public abstract Property<String> getContractNameTemplate();

    /**
     * Whether the Kotlin contracts use {@code suspend} functions.
     *
     * @return the property
     */
    public abstract Property<Boolean> getKotlinSuspend();

    /**
     * The class name of the return type of a subscription field.
     *
     * @return the property
     */
    public abstract Property<String> getSubscriptionReturnType();

    /**
     * The classes of custom scalars and of single fields, keyed by the SDL name or by a field coordinate.
     *
     * @return the property
     */
    public abstract MapProperty<String, String> getTypeMappings();

    /**
     * {@code NONE}, {@code TYPES} or {@code FIELDS} (the default).
     *
     * @return the property
     */
    public abstract Property<String> getNameConstants();

    /**
     * The globs that select the schema files, relative to a schema directory.
     *
     * @return the property
     */
    public abstract ListProperty<String> getIncludes();

    /**
     * The globs that remove schema files from the selection.
     *
     * @return the property
     */
    public abstract ListProperty<String> getExcludes();

    /**
     * Whether comments in the schema become descriptions.
     *
     * @return the property
     */
    public abstract Property<Boolean> getCommentsAsDescriptions();

    /**
     * The options in their string form: the keys of the generator's {@code Options.fromMap}, only for the options that are set.
     *
     * @return the options, in key order
     */
    Map<String, String> toOptionMap() {
        Map<String, String> map = new TreeMap<>();
        put(map, "language", getLanguage());
        put(map, "mode", getMode());
        put(map, "packageName", getPackageName());
        put(map, "modelPackage", getModelPackage());
        put(map, "resolverPackage", getResolverPackage());
        put(map, "namesPackage", getNamesPackage());
        put(map, "generatedAnnotation", getGeneratedAnnotation());
        put(map, "modelStyle", getModelStyle());
        putList(map, "interfaceTypes", getInterfaceTypes());
        put(map, "typeNamePrefix", getTypeNamePrefix());
        put(map, "typeNameSuffix", getTypeNameSuffix());
        put(map, "nullableAnnotation", getNullableAnnotation());
        put(map, "rootContracts", getRootContracts());
        put(map, "contractNameTemplate", getContractNameTemplate());
        put(map, "kotlinSuspend", getKotlinSuspend());
        put(map, "subscriptionReturnType", getSubscriptionReturnType());
        getTypeMappings().getOrElse(Map.of()).forEach((key, value) -> map.put("typeMappings." + key, value));
        put(map, "nameConstants", getNameConstants());
        putList(map, "includes", getIncludes());
        putList(map, "excludes", getExcludes());
        put(map, "commentsAsDescriptions", getCommentsAsDescriptions());
        return map;
    }

    /**
     * The schema files: each schema directory narrowed by {@code includes} and {@code excludes}.
     *
     * @param project the project that owns the file trees
     * @return one file tree per directory
     */
    List<FileTree> schemaFileTrees(Project project) {
        List<String> includes = getIncludes().getOrElse(DEFAULT_INCLUDES);
        List<String> excludes = getExcludes().getOrElse(List.of());
        List<FileTree> trees = new ArrayList<>();
        for (File dir : getSchemaDirs().getFiles()) {
            trees.add(project.fileTree(dir).matching(filter -> filter.include(includes).exclude(excludes)));
        }
        return trees;
    }

    private static void put(Map<String, String> map, String key, Property<?> property) {
        if (property.isPresent()) {
            map.put(key, String.valueOf(property.get()));
        }
    }

    private static void putList(Map<String, String> map, String key, ListProperty<String> property) {
        List<String> values = property.get();
        if (!values.isEmpty()) {
            map.put(key + ".size", String.valueOf(values.size()));
            for (int i = 0; i < values.size(); i++) {
                map.put(key + "." + i, values.get(i));
            }
        }
    }
}
