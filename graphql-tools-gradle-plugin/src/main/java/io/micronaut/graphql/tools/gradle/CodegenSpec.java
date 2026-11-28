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
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;
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
     * The directory that the task writes the generated resources to, and that is added to the resources of the main source set:
     * the type mappings file {@code META-INF/micronaut-graphql-tools/type-mappings.properties} of the module. The task recreates it
     * before every run. The default is {@code build/generated/resources/graphqlTools/<name>}.
     *
     * @return the directory
     */
    public abstract DirectoryProperty getResourcesDirectory();

    /**
     * The generated language: {@code JAVA}, {@code KOTLIN} or {@code GROOVY}. The default is {@code JAVA}, also in a Kotlin or Groovy
     * project: the generator emits Java only, which those projects compile together with their own sources.
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
     * {@code AUTO} (the default) or {@code NEVER}: whether unions and interfaces are sealed interfaces that permit their members.
     *
     * @return the property
     */
    public abstract Property<String> getSealedAbstractTypes();

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
     * Whether the type mappings file of each dependency of the project is read. The plugin gives the generator the jars and class
     * directories of the {@code implementation} and {@code compileOnly} dependencies (project dependencies as jars), so a type that a
     * dependency maps needs no {@code typeMappings} entry. The default is {@code true}; with {@code false} the task does not read the
     * dependencies, and they are not an input of the task.
     *
     * @return the property
     */
    public abstract Property<Boolean> getTypeMappingsFromDependencies();

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
     * The globs that select the operation documents of client mode, relative to the directory that holds them.
     *
     * @return the property
     */
    public abstract ListProperty<String> getOperationIncludes();

    /**
     * The globs that remove operation documents from the selection.
     *
     * @return the property
     */
    public abstract ListProperty<String> getOperationExcludes();

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
        put(map, "sealedAbstractTypes", getSealedAbstractTypes());
        put(map, "typeNamePrefix", getTypeNamePrefix());
        put(map, "typeNameSuffix", getTypeNameSuffix());
        put(map, "nullableAnnotation", getNullableAnnotation());
        put(map, "rootContracts", getRootContracts());
        put(map, "contractNameTemplate", getContractNameTemplate());
        put(map, "kotlinSuspend", getKotlinSuspend());
        put(map, "subscriptionReturnType", getSubscriptionReturnType());
        getTypeMappings().getOrElse(Map.of()).forEach((key, value) -> map.put("typeMappings." + key, value));
        put(map, "typeMappingsFromDependencies", getTypeMappingsFromDependencies());
        put(map, "nameConstants", getNameConstants());
        putList(map, "includes", getIncludes());
        putList(map, "excludes", getExcludes());
        put(map, "commentsAsDescriptions", getCommentsAsDescriptions());
        putList(map, "operationIncludes", getOperationIncludes());
        putList(map, "operationExcludes", getOperationExcludes());
        return map;
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
