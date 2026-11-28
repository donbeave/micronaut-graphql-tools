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
package io.micronaut.graphql.tools.codegen;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The options of the generator, and the single source of truth for them: the Gradle extension, the Maven mojo parameters and the
 * builder of the library map onto the components of this record by name. Schema files and the output directory are not options;
 * they are arguments of {@link GraphQLCodeGenerator#builder()}.
 *
 * <p>Every component has a string form, so the options cross Gradle worker isolation and Maven XML binding without a custom type:
 * {@link #toMap()} and {@link #fromMap(Map)}. A {@code String} is itself, a {@code boolean} is {@code true} or {@code false}, an
 * enum is its constant name, a list is {@code name.size} followed by {@code name.0}, {@code name.1} and so on, and a map is
 * {@code name.<key>}. {@code toMap()} lists only the values that differ from the default, in key order, so the form is canonical,
 * and {@code fromMap(toMap(options))} gives back an equal record.</p>
 *
 * <p>The names are in GraphQL vocabulary and do not reuse the names of the options of the compile-time processor. A record
 * accepts any value; {@link #validate()} reports the invalid ones and combinations as diagnostics.</p>
 *
 * @param language               the language of the generated sources. Default {@code JAVA}.
 * @param mode                   what is generated: a server or a client. Default {@code SERVER}. Client mode generates Java only.
 * @param packageName            the Java package of the generated sources. Required: the default is the empty string, and
 *                               {@link #validate()} reports it.
 * @param modelPackage           the package of the model, input and enum types. Default is the empty string, which means
 *                               {@code <packageName>.model}.
 * @param resolverPackage        the package of the resolver contracts. Default is the empty string, which means
 *                               {@code <packageName>.resolver}.
 * @param namesPackage           the package of the name constants. Default is the empty string, which means
 *                               {@code <packageName>.names}.
 * @param generatedAnnotation    the class name of the annotation that marks generated types, or the empty string for none. Default
 *                               {@code javax.annotation.processing.Generated}, written without a date.
 * @param modelStyle             the shape of a model type. Default {@code RECORD}.
 * @param interfaceTypes         the SDL names of the object types that are generated as interfaces. Default empty.
 * @param sealedAbstractTypes    whether a union and an interface are generated as {@code sealed} interfaces that permit their
 *                               members. Default {@code AUTO}: sealed when every member is a generated model type, and a plain
 *                               interface, with a diagnostic that says why, otherwise. {@code NEVER} generates plain interfaces.
 * @param typeNamePrefix         a prefix for the name of every generated type. Default is the empty string.
 * @param typeNameSuffix         a suffix for the name of every generated type. Default is the empty string.
 * @param nullableAnnotation     the class name of the annotation on a nullable type, or the empty string for none. Default
 *                               {@code io.micronaut.core.annotation.Nullable} in server mode and
 *                               {@code org.jspecify.annotations.Nullable} in client mode.
 * @param rootContracts          whether a resolver contract is generated for the root types. Default {@code true}.
 * @param contractNameTemplate   the name of a resolver contract, with the placeholders {@code {Field}} (the field name with a
 *                               capital first letter) and {@code {Operation}} (the root type name). Default
 *                               {@code {Field}{Operation}Resolver}.
 * @param kotlinSuspend          whether a Kotlin contract method is {@code suspend}. Default {@code false}; it needs the language
 *                               {@code KOTLIN}.
 * @param subscriptionReturnType the class name of the type that a subscription method returns. Default
 *                               {@code org.reactivestreams.Publisher}.
 * @param typeMappings           the classes of custom scalars and of single fields, keyed by the SDL name ({@code Date}) or by
 *                               {@code Type.field}. A type-level key names a scalar, or an object, enum or input type that is an
 *                               existing class and generates nothing; {@code Type.field} maps a field whose type is a scalar, and is an
 *                               error for a field of another kind. Default empty, plus the presets of {@code ScalarPresets}; an
 *                               unmapped custom scalar is a diagnostic, never a silent {@code String}.
 * @param typeMappingsFromDependencies whether the generator reads {@code META-INF/micronaut-graphql-tools/type-mappings.properties} from
 *                               the dependency jars that the caller gives it, and applies the mappings below {@code typeMappings}.
 *                               Default {@code true}; it has no effect without dependencies. The generator always writes the file
 *                               for its own output.
 * @param nameConstants          which name constants are generated. Default {@code FIELDS}.
 * @param includes               the globs that select the schema files, relative to the schema root. Default
 *                               {@code **}{@code /*.graphqls} and {@code **}{@code /*.graphql}.
 * @param excludes               the globs that remove schema files from the selection. Default empty.
 * @param commentsAsDescriptions whether {@code #} comments before a definition are its description. Default {@code false}; it must
 *                               match the runtime setting.
 * @param operationIncludes      the globs that select the operation documents of client mode, relative to the directory that holds
 *                               them. Default {@code **}{@code /*.graphql}. It applies to a directory only; documents that the caller
 *                               gives as files or as text are used as given.
 * @param operationExcludes      the globs that remove operation documents from the selection. Default empty.
 */
public record Options(
        Language language,
        Mode mode,
        String packageName,
        String modelPackage,
        String resolverPackage,
        String namesPackage,
        String generatedAnnotation,
        ModelStyle modelStyle,
        List<String> interfaceTypes,
        SealedAbstractTypes sealedAbstractTypes,
        String typeNamePrefix,
        String typeNameSuffix,
        String nullableAnnotation,
        boolean rootContracts,
        String contractNameTemplate,
        boolean kotlinSuspend,
        String subscriptionReturnType,
        Map<String, String> typeMappings,
        boolean typeMappingsFromDependencies,
        NameConstants nameConstants,
        List<String> includes,
        List<String> excludes,
        boolean commentsAsDescriptions,
        List<String> operationIncludes,
        List<String> operationExcludes) {

    /** The default of {@code generatedAnnotation}. */
    public static final String DEFAULT_GENERATED_ANNOTATION = "javax.annotation.processing.Generated";

    /** The default of {@code nullableAnnotation} in server mode. */
    public static final String DEFAULT_SERVER_NULLABLE_ANNOTATION = "io.micronaut.core.annotation.Nullable";

    /** The default of {@code nullableAnnotation} in client mode. */
    public static final String DEFAULT_CLIENT_NULLABLE_ANNOTATION = "org.jspecify.annotations.Nullable";

    /** The default of {@code contractNameTemplate}. */
    public static final String DEFAULT_CONTRACT_NAME_TEMPLATE = "{Field}{Operation}Resolver";

    /** The default of {@code subscriptionReturnType}. */
    public static final String DEFAULT_SUBSCRIPTION_RETURN_TYPE = "org.reactivestreams.Publisher";

    /** The default of {@code includes}. */
    public static final List<String> DEFAULT_INCLUDES = List.of("**/*.graphqls", "**/*.graphql");

    /** The default of {@code operationIncludes}. */
    public static final List<String> DEFAULT_OPERATION_INCLUDES = List.of("**/*.graphql");

    /**
     * Creates the options, keeping immutable copies of the lists and the map.
     */
    public Options {
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(packageName, "packageName");
        Objects.requireNonNull(modelPackage, "modelPackage");
        Objects.requireNonNull(resolverPackage, "resolverPackage");
        Objects.requireNonNull(namesPackage, "namesPackage");
        Objects.requireNonNull(generatedAnnotation, "generatedAnnotation");
        Objects.requireNonNull(modelStyle, "modelStyle");
        Objects.requireNonNull(sealedAbstractTypes, "sealedAbstractTypes");
        Objects.requireNonNull(typeNamePrefix, "typeNamePrefix");
        Objects.requireNonNull(typeNameSuffix, "typeNameSuffix");
        Objects.requireNonNull(nullableAnnotation, "nullableAnnotation");
        Objects.requireNonNull(contractNameTemplate, "contractNameTemplate");
        Objects.requireNonNull(subscriptionReturnType, "subscriptionReturnType");
        Objects.requireNonNull(nameConstants, "nameConstants");
        interfaceTypes = List.copyOf(interfaceTypes);
        includes = List.copyOf(includes);
        excludes = List.copyOf(excludes);
        operationIncludes = List.copyOf(operationIncludes);
        operationExcludes = List.copyOf(operationExcludes);
        typeMappings = Collections.unmodifiableMap(new LinkedHashMap<>(typeMappings));
        typeMappings.forEach((key, value) -> {
            Objects.requireNonNull(key, "typeMappings key");
            Objects.requireNonNull(value, "typeMappings value");
        });
    }

    /**
     * The default {@code nullableAnnotation} of a mode.
     *
     * @param mode the mode
     * @return the class name
     */
    public static String defaultNullableAnnotation(Mode mode) {
        return mode == Mode.CLIENT ? DEFAULT_CLIENT_NULLABLE_ANNOTATION : DEFAULT_SERVER_NULLABLE_ANNOTATION;
    }

    /**
     * A builder that starts with the defaults. Set {@code packageName}, which has none.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A builder that starts with the values of these options.
     *
     * @return the builder
     */
    public Builder toBuilder() {
        return new Builder()
                .language(language).mode(mode).packageName(packageName).modelPackage(modelPackage)
                .resolverPackage(resolverPackage).namesPackage(namesPackage).generatedAnnotation(generatedAnnotation)
                .modelStyle(modelStyle).interfaceTypes(interfaceTypes).sealedAbstractTypes(sealedAbstractTypes)
                .typeNamePrefix(typeNamePrefix)
                .typeNameSuffix(typeNameSuffix).nullableAnnotation(nullableAnnotation).rootContracts(rootContracts)
                .contractNameTemplate(contractNameTemplate).kotlinSuspend(kotlinSuspend)
                .subscriptionReturnType(subscriptionReturnType).typeMappings(typeMappings)
                .typeMappingsFromDependencies(typeMappingsFromDependencies).nameConstants(nameConstants)
                .includes(includes).excludes(excludes).commentsAsDescriptions(commentsAsDescriptions)
                .operationIncludes(operationIncludes).operationExcludes(operationExcludes);
    }

    /**
     * The package of the model types: {@code modelPackage}, or {@code <packageName>.model} when it is empty.
     *
     * @return the package
     */
    public String effectiveModelPackage() {
        return modelPackage.isEmpty() ? packageName + ".model" : modelPackage;
    }

    /**
     * The package of the resolver contracts: {@code resolverPackage}, or {@code <packageName>.resolver} when it is empty.
     *
     * @return the package
     */
    public String effectiveResolverPackage() {
        return resolverPackage.isEmpty() ? packageName + ".resolver" : resolverPackage;
    }

    /**
     * The package of the name constants: {@code namesPackage}, or {@code <packageName>.names} when it is empty.
     *
     * @return the package
     */
    public String effectiveNamesPackage() {
        return namesPackage.isEmpty() ? packageName + ".names" : namesPackage;
    }

    /**
     * The string form of these options: only the values that differ from the default, sorted by key. See the description of this
     * record for the form of each component.
     *
     * @return an immutable map that {@link #fromMap(Map)} reads back to equal options
     */
    public Map<String, String> toMap() {
        return OptionsForm.toMap(this);
    }

    /**
     * Reads options from their string form. A key that is absent has its default. An unknown key, a value that does not parse (the
     * diagnostic lists the valid constants of an enum) and an incomplete list each give a diagnostic that names the option, and the
     * option keeps its default. The result does not validate the combinations; call {@link #validate()} for that.
     *
     * @param map the string form, as {@link #toMap()} writes it
     * @return the options and the diagnostics
     */
    public static OptionsResult fromMap(Map<String, String> map) {
        return OptionsForm.fromMap(map);
    }

    /**
     * Checks the values and the combinations of these options. It never throws for an invalid value.
     *
     * @return the diagnostics, each naming an option, in a stable order; empty when the options are valid
     */
    public List<Diagnostic> validate() {
        return OptionsValidator.validate(this);
    }

    /**
     * A builder of {@link Options}. A value that is not set has its default; {@code nullableAnnotation} follows the mode.
     */
    public static final class Builder {

        private Language language = Language.JAVA;
        private Mode mode = Mode.SERVER;
        private String packageName = "";
        private String modelPackage = "";
        private String resolverPackage = "";
        private String namesPackage = "";
        private String generatedAnnotation = DEFAULT_GENERATED_ANNOTATION;
        private ModelStyle modelStyle = ModelStyle.RECORD;
        private List<String> interfaceTypes = List.of();
        private SealedAbstractTypes sealedAbstractTypes = SealedAbstractTypes.AUTO;
        private String typeNamePrefix = "";
        private String typeNameSuffix = "";
        private String nullableAnnotation;
        private boolean rootContracts = true;
        private String contractNameTemplate = DEFAULT_CONTRACT_NAME_TEMPLATE;
        private boolean kotlinSuspend;
        private String subscriptionReturnType = DEFAULT_SUBSCRIPTION_RETURN_TYPE;
        private Map<String, String> typeMappings = Map.of();
        private boolean typeMappingsFromDependencies = true;
        private NameConstants nameConstants = NameConstants.FIELDS;
        private List<String> includes = DEFAULT_INCLUDES;
        private List<String> excludes = List.of();
        private boolean commentsAsDescriptions;
        private List<String> operationIncludes = DEFAULT_OPERATION_INCLUDES;
        private List<String> operationExcludes = List.of();

        Builder() {
        }

        /**
         * Sets {@code language}.
         *
         * @param value the value
         * @return this builder
         */
        public Builder language(Language value) {
            this.language = value;
            return this;
        }

        /**
         * Sets {@code mode}.
         *
         * @param value the value
         * @return this builder
         */
        public Builder mode(Mode value) {
            this.mode = value;
            return this;
        }

        /**
         * Sets {@code packageName}.
         *
         * @param value the value
         * @return this builder
         */
        public Builder packageName(String value) {
            this.packageName = value;
            return this;
        }

        /**
         * Sets {@code modelPackage}.
         *
         * @param value the value
         * @return this builder
         */
        public Builder modelPackage(String value) {
            this.modelPackage = value;
            return this;
        }

        /**
         * Sets {@code resolverPackage}.
         *
         * @param value the value
         * @return this builder
         */
        public Builder resolverPackage(String value) {
            this.resolverPackage = value;
            return this;
        }

        /**
         * Sets {@code namesPackage}.
         *
         * @param value the value
         * @return this builder
         */
        public Builder namesPackage(String value) {
            this.namesPackage = value;
            return this;
        }

        /**
         * Sets {@code generatedAnnotation}.
         *
         * @param value the value
         * @return this builder
         */
        public Builder generatedAnnotation(String value) {
            this.generatedAnnotation = value;
            return this;
        }

        /**
         * Sets {@code modelStyle}.
         *
         * @param value the value
         * @return this builder
         */
        public Builder modelStyle(ModelStyle value) {
            this.modelStyle = value;
            return this;
        }

        /**
         * Sets {@code interfaceTypes}.
         *
         * @param value the value
         * @return this builder
         */
        public Builder interfaceTypes(List<String> value) {
            this.interfaceTypes = List.copyOf(value);
            return this;
        }

        /**
         * Sets {@code sealedAbstractTypes}.
         *
         * @param value the value
         * @return this builder
         */
        public Builder sealedAbstractTypes(SealedAbstractTypes value) {
            this.sealedAbstractTypes = value;
            return this;
        }

        /**
         * Sets {@code typeNamePrefix}.
         *
         * @param value the value
         * @return this builder
         */
        public Builder typeNamePrefix(String value) {
            this.typeNamePrefix = value;
            return this;
        }

        /**
         * Sets {@code typeNameSuffix}.
         *
         * @param value the value
         * @return this builder
         */
        public Builder typeNameSuffix(String value) {
            this.typeNameSuffix = value;
            return this;
        }

        /**
         * Sets {@code nullableAnnotation}. When it is never set, the default of the mode applies.
         *
         * @param value the value
         * @return this builder
         */
        public Builder nullableAnnotation(String value) {
            this.nullableAnnotation = value;
            return this;
        }

        /**
         * Sets {@code rootContracts}.
         *
         * @param value the value
         * @return this builder
         */
        public Builder rootContracts(boolean value) {
            this.rootContracts = value;
            return this;
        }

        /**
         * Sets {@code contractNameTemplate}.
         *
         * @param value the value
         * @return this builder
         */
        public Builder contractNameTemplate(String value) {
            this.contractNameTemplate = value;
            return this;
        }

        /**
         * Sets {@code kotlinSuspend}.
         *
         * @param value the value
         * @return this builder
         */
        public Builder kotlinSuspend(boolean value) {
            this.kotlinSuspend = value;
            return this;
        }

        /**
         * Sets {@code subscriptionReturnType}.
         *
         * @param value the value
         * @return this builder
         */
        public Builder subscriptionReturnType(String value) {
            this.subscriptionReturnType = value;
            return this;
        }

        /**
         * Sets {@code typeMappings}.
         *
         * @param value the value
         * @return this builder
         */
        public Builder typeMappings(Map<String, String> value) {
            this.typeMappings = new LinkedHashMap<>(value);
            return this;
        }

        /**
         * Sets {@code typeMappingsFromDependencies}.
         *
         * @param value the value
         * @return this builder
         */
        public Builder typeMappingsFromDependencies(boolean value) {
            this.typeMappingsFromDependencies = value;
            return this;
        }

        /**
         * Sets {@code nameConstants}.
         *
         * @param value the value
         * @return this builder
         */
        public Builder nameConstants(NameConstants value) {
            this.nameConstants = value;
            return this;
        }

        /**
         * Sets {@code includes}.
         *
         * @param value the value
         * @return this builder
         */
        public Builder includes(List<String> value) {
            this.includes = List.copyOf(value);
            return this;
        }

        /**
         * Sets {@code excludes}.
         *
         * @param value the value
         * @return this builder
         */
        public Builder excludes(List<String> value) {
            this.excludes = List.copyOf(value);
            return this;
        }

        /**
         * Sets {@code commentsAsDescriptions}.
         *
         * @param value the value
         * @return this builder
         */
        public Builder commentsAsDescriptions(boolean value) {
            this.commentsAsDescriptions = value;
            return this;
        }

        /**
         * Sets {@code operationIncludes}.
         *
         * @param value the value
         * @return this builder
         */
        public Builder operationIncludes(List<String> value) {
            this.operationIncludes = List.copyOf(value);
            return this;
        }

        /**
         * Sets {@code operationExcludes}.
         *
         * @param value the value
         * @return this builder
         */
        public Builder operationExcludes(List<String> value) {
            this.operationExcludes = List.copyOf(value);
            return this;
        }

        /**
         * Builds the options.
         *
         * @return the options
         */
        public Options build() {
            String nullable = nullableAnnotation != null ? nullableAnnotation : defaultNullableAnnotation(mode);
            return new Options(language, mode, packageName, modelPackage, resolverPackage, namesPackage, generatedAnnotation,
                    modelStyle, interfaceTypes, sealedAbstractTypes, typeNamePrefix, typeNameSuffix, nullable, rootContracts,
                    contractNameTemplate, kotlinSuspend, subscriptionReturnType, typeMappings,
                    typeMappingsFromDependencies, nameConstants,
                    includes, excludes, commentsAsDescriptions, operationIncludes, operationExcludes);
        }
    }
}
