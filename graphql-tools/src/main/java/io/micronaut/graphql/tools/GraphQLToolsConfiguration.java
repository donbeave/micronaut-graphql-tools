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
package io.micronaut.graphql.tools;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaLocation;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Options of Micronaut GraphQL Tools, under {@value #PREFIX}.
 */
@ConfigurationProperties(GraphQLToolsConfiguration.PREFIX)
public class GraphQLToolsConfiguration {

    public static final String PREFIX = "micronaut.graphql.tools";

    private Precedence precedence = Precedence.STRICT;
    private List<String> resolverDirectives = Collections.emptyList();
    private boolean strict = true;
    private Map<String, String> checks = Collections.emptyMap();
    private AllowConfiguration allow = new AllowConfiguration();
    private boolean eagerInit = true;
    private SchemaConfiguration schema = new SchemaConfiguration();
    private ManifestConfiguration manifest = new ManifestConfiguration();

    /**
     * @return how a field is bound when both a member of its Java type (a property or a {@code @GraphQLField} method)
     * and a type resolver method can bind it
     */
    @NonNull
    public Precedence getPrecedence() {
        return precedence;
    }

    /**
     * Sets how a field is bound when both a member of its Java type (a property or a {@code @GraphQLField} method) and
     * a type resolver method can bind it. Default: {@link Precedence#STRICT}.
     *
     * @param precedence the precedence
     */
    public void setPrecedence(@NonNull Precedence precedence) {
        ArgumentUtils.requireNonNull("precedence", precedence);
        this.precedence = precedence;
    }

    /**
     * @return the names of the schema directives that mark a field as bound by a type resolver
     */
    @NonNull
    public List<String> getResolverDirectives() {
        return resolverDirectives;
    }

    /**
     * Sets the names (without {@code @}) of the schema directives that mark a field as bound by a type resolver. A
     * field that carries one of them is bound by its type resolver method, whatever {@link #getPrecedence()} says,
     * and fails the startup when no type resolver method binds it. Every name must be a directive the schema declares.
     * Default: empty.
     *
     * @param resolverDirectives the directive names
     */
    public void setResolverDirectives(@NonNull List<String> resolverDirectives) {
        ArgumentUtils.requireNonNull("resolverDirectives", resolverDirectives);
        this.resolverDirectives = List.copyOf(resolverDirectives);
    }

    /**
     * @return the shorthand for every check that has no {@code checks} entry
     */
    public boolean isStrict() {
        return strict;
    }

    /**
     * Sets the shorthand for the severity of every check that has no entry in {@link #getChecks()}: {@code true}
     * (the default) is every check at its default severity, {@code fail}; {@code false} is every check {@code warn}.
     * An explicit entry in {@link #getChecks()} wins. Default: {@code true}.
     *
     * @param strict whether the checks fail by default
     */
    public void setStrict(boolean strict) {
        this.strict = strict;
    }

    /**
     * @return the severity of each check by its code, as written: {@code fail}, {@code warn} or {@code ignore}
     */
    @NonNull
    public Map<String, String> getChecks() {
        return checks;
    }

    /**
     * Sets the severity of single checks, by their codes: {@code unbound-resolver-method},
     * {@code root-method-several-operations}, {@code unbound-enum-constant}, {@code unmapped-input-property},
     * {@code nullable-primitive}, {@code unreachable-type}, {@code unreachable-value-type},
     * {@code unused-dictionary-entry}, {@code interface-read-through-implementation} and {@code unwired-directive}.
     * {@code fail} fails the startup, {@code warn} logs the finding and goes on, {@code ignore} skips the check. An
     * unknown code or severity fails the startup. The entries win over {@link #isStrict()}. Default: empty.
     *
     * @param checks the severity of each check by its code
     */
    public void setChecks(@NonNull Map<String, String> checks) {
        ArgumentUtils.requireNonNull("checks", checks);
        this.checks = Map.copyOf(checks);
    }

    /**
     * @return the findings, types and directives the checks accept, under {@value #PREFIX}.allow
     */
    @NonNull
    public AllowConfiguration getAllow() {
        return allow;
    }

    /**
     * @param allow the findings, types and directives the checks accept
     */
    public void setAllow(@NonNull AllowConfiguration allow) {
        ArgumentUtils.requireNonNull("allow", allow);
        this.allow = allow;
    }

    /**
     * @return whether the schema is bound when the application context starts
     */
    public boolean isEagerInit() {
        return eagerInit;
    }

    /**
     * Sets whether the schema is bound when the application context starts, so that a broken binding fails the
     * startup and the first request does no binding work. {@code false} binds it when the first bean that needs the
     * {@link graphql.schema.GraphQLSchema} is created, which is what tests that expect the binding to fail on a
     * lookup need. Default: {@code true}.
     *
     * @param eagerInit whether to bind at startup
     */
    public void setEagerInit(boolean eagerInit) {
        this.eagerInit = eagerInit;
    }

    /**
     * @return the options of the schema files, under {@value #PREFIX}.schema
     */
    @NonNull
    public SchemaConfiguration getSchema() {
        return schema;
    }

    /**
     * @param schema the options of the schema files
     */
    public void setSchema(@NonNull SchemaConfiguration schema) {
        ArgumentUtils.requireNonNull("schema", schema);
        this.schema = schema;
    }

    /**
     * @return the options of the binding manifest, under {@value #PREFIX}.manifest
     */
    @NonNull
    public ManifestConfiguration getManifest() {
        return manifest;
    }

    /**
     * @param manifest the options of the binding manifest
     */
    public void setManifest(@NonNull ManifestConfiguration manifest) {
        ArgumentUtils.requireNonNull("manifest", manifest);
        this.manifest = manifest;
    }

    /**
     * Options of the schema files, under {@value GraphQLToolsConfiguration#PREFIX}{@code .schema}.
     */
    @ConfigurationProperties("schema")
    public static class SchemaConfiguration {

        private List<String> locations = List.of(SchemaLocation.DEFAULT);

        /**
         * @return where the schema files are read from when the application provides no {@code TypeDefinitionRegistry} bean
         */
        @NonNull
        public List<String> getLocations() {
            return locations;
        }

        /**
         * Sets where the schema files are read from when the application provides no {@code TypeDefinitionRegistry} bean:
         * {@code classpath:} (the first classpath entry that holds the directory), {@code classpath*:} (every entry,
         * including dependency jars) or {@code file:}, followed by an Ant-style path with {@code *}, {@code **} and
         * {@code ?}. A file that holds only executable definitions (client operations) is skipped; a schema without a
         * {@code schema} block uses the types {@code Query}, {@code Mutation} and {@code Subscription} as its roots.
         * Default: {@code classpath:graphql/**}{@code /*.graphqls}.
         *
         * @param locations the locations
         */
        public void setLocations(@NonNull List<String> locations) {
            ArgumentUtils.requireNonNull("locations", locations);
            this.locations = List.copyOf(locations);
        }

    }

    /**
     * Options of the binding manifest, under {@value GraphQLToolsConfiguration#PREFIX}{@code .manifest}.
     */
    @ConfigurationProperties("manifest")
    public static class ManifestConfiguration {

        private boolean enabled;
        private boolean record;
        @Nullable
        private String path;

        /**
         * @return whether the binding manifest is recorded while the schema is bound, which is the case when it is
         * recorded explicitly or written at startup
         */
        public boolean isRecorded() {
            return record || enabled;
        }

        /**
         * @return whether the binding manifest is recorded explicitly, so that the {@link BindingManifest} bean can be
         * read
         */
        public boolean isRecord() {
            return record;
        }

        /**
         * Sets whether the binding manifest is recorded while the schema is bound, so that the
         * {@link BindingManifest} bean can be read. Default: {@code false}: nothing is recorded, the bean cannot be
         * created, and the strict-mode checks that only feed the manifest are skipped when
         * {@link GraphQLToolsConfiguration#isStrict()} is {@code false} too. {@link #isEnabled()} implies it.
         *
         * @param record whether to record it
         */
        public void setRecord(boolean record) {
            this.record = record;
        }

        /**
         * @return whether the binding manifest is written at startup
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Sets whether the binding manifest is written at startup: to {@link #getPath()}, or to the log when no path is
         * set. Default: {@code false}. This records the manifest, see {@link #setRecord(boolean)}.
         *
         * @param enabled whether to write it
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * @return the file the binding manifest is written to, or {@code null} to log it
         */
        @Nullable
        public String getPath() {
            return path;
        }

        /**
         * Sets the file the binding manifest is written to at startup; its directories are created. Default: none,
         * the manifest is logged at INFO.
         *
         * @param path the file
         */
        public void setPath(@Nullable String path) {
            this.path = path;
        }

    }

    /**
     * What the checks accept, under {@value GraphQLToolsConfiguration#PREFIX}.allow. Every entry is validated at
     * startup; an entry that no longer applies fails it, so the lists cannot go stale.
     */
    @ConfigurationProperties("allow")
    public static class AllowConfiguration {

        private List<String> unreachable = Collections.emptyList();
        private List<String> inertDirectives = Collections.emptyList();
        private List<String> schemaCustomizerDirectives = Collections.emptyList();
        private List<String> findings = Collections.emptyList();

        /**
         * @return the names of unreachable types that the checks accept
         */
        @NonNull
        public List<String> getUnreachable() {
            return unreachable;
        }

        /**
         * Sets the names of unreachable types that the checks accept: an object, interface or union type, or an enum,
         * input or scalar type. An unreachable type keeps graphql-java's default data fetchers, and the types its
         * fields use are accepted with it. Every name must be an unreachable type of the schema; any other name fails
         * the startup. Default: empty.
         *
         * @param unreachable the type names
         */
        public void setUnreachable(@NonNull List<String> unreachable) {
            ArgumentUtils.requireNonNull("unreachable", unreachable);
            this.unreachable = List.copyOf(unreachable);
        }

        /**
         * @return the directives the schema may apply without a wiring, because they have no effect at runtime
         */
        @NonNull
        public List<String> getInertDirectives() {
            return inertDirectives;
        }

        /**
         * Sets the directives the schema may apply without a wiring, because they have no effect at runtime (names
         * without {@code @}; an entry with {@code *} is a pattern, such as {@code link*}, and must match a declared
         * directive that lacks a wiring). Every name must be a directive the schema declares and that has no wiring;
         * any other name fails the startup, so the list cannot go stale. Default: empty.
         *
         * @param inertDirectives the directive names
         */
        public void setInertDirectives(@NonNull List<String> inertDirectives) {
            ArgumentUtils.requireNonNull("inertDirectives", inertDirectives);
            this.inertDirectives = List.copyOf(inertDirectives);
        }

        /**
         * @return the directives that a {@link GraphQLSchemaCustomizer} handles where graphql-java calls no wiring
         */
        @NonNull
        public List<String> getSchemaCustomizerDirectives() {
            return schemaCustomizerDirectives;
        }

        /**
         * Sets the directives (names without {@code @}) that a {@link GraphQLSchemaCustomizer} handles on the schema
         * definition and on the arguments of directive definitions. graphql-java calls no
         * {@link graphql.schema.idl.SchemaDirectiveWiring} there, so a wiring does not count for those places: a
         * directive applied there needs an entry in this list or in {@link #getInertDirectives()}. Every name must be
         * a directive the schema declares, that is not built in and that is applied on one of those places. Default:
         * empty.
         *
         * @param schemaCustomizerDirectives the directive names
         */
        public void setSchemaCustomizerDirectives(@NonNull List<String> schemaCustomizerDirectives) {
            ArgumentUtils.requireNonNull("schemaCustomizerDirectives", schemaCustomizerDirectives);
            this.schemaCustomizerDirectives = List.copyOf(schemaCustomizerDirectives);
        }

        /**
         * @return the manifest lines of findings that the checks accept
         */
        @NonNull
        public List<String> getFindings() {
            return findings;
        }

        /**
         * Sets the findings that the checks accept, as lines of the binding manifest, exactly as they are written
         * there: {@code !unbound-resolver-method example.Query#helper(java.lang.String)}. A listed finding neither
         * fails the startup nor is logged, and it does not appear in the manifest. Every entry must be a {@code !}
         * line of a check that is not {@code ignore} and must match a finding of this startup; any other entry fails
         * the startup. Default: empty.
         *
         * @param findings the manifest lines
         */
        public void setFindings(@NonNull List<String> findings) {
            ArgumentUtils.requireNonNull("findings", findings);
            this.findings = List.copyOf(findings);
        }

    }

    /**
     * How a field is bound when both a member of its Java type (a property or a {@code @GraphQLField} method) and a
     * type resolver method (of the type or of one of its interfaces) can bind it. A field that carries a directive
     * listed in {@link #getResolverDirectives()} is always bound by the type resolver method.
     */
    public enum Precedence {

        /**
         * Fail the startup with {@link io.micronaut.graphql.tools.exceptions.AmbiguousBindingException}, naming the
         * field and both members.
         */
        STRICT,

        /**
         * Bind the member of the Java type; the type resolver method is not called. This was the only behaviour before
         * the option existed.
         */
        PROPERTY_FIRST,

        /**
         * Bind the type resolver method; the member of the Java type is not read.
         */
        RESOLVER_FIRST

    }

}
