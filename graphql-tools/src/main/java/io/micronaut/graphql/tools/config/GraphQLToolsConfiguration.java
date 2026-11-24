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
package io.micronaut.graphql.tools.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.ArgumentUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Options of Micronaut GraphQL Tools, under {@value #PREFIX}.
 */
@ConfigurationProperties(GraphQLToolsConfiguration.PREFIX)
public class GraphQLToolsConfiguration {

    public static final String PREFIX = "micronaut.graphql.tools";

    private Precedence precedence = Precedence.FAIL;
    private List<String> resolverDirectives = Collections.emptyList();
    private boolean strict = true;
    private boolean resolverAccessorPrefixes;
    private String typeClassConvention;
    private Map<String, CheckSeverity> checks = Collections.emptyMap();
    private AllowConfiguration allow = new AllowConfiguration();
    private boolean eagerInit = true;
    private SchemaConfiguration schema = new SchemaConfiguration();
    private ManifestConfiguration manifest = new ManifestConfiguration();
    private ExecutionConfiguration execution = new ExecutionConfiguration();
    private ErrorsConfiguration errors = new ErrorsConfiguration();
    private ProtectionConfiguration protection = new ProtectionConfiguration();

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
     * a type resolver method can bind it. Independent of {@link #isStrict()}.
     * Default: {@link Precedence#FAIL}.
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
     * @return whether a resolver method named {@code getX} or {@code isX} also binds the field {@code x}
     */
    public boolean isResolverAccessorPrefixes() {
        return resolverAccessorPrefixes;
    }

    /**
     * Sets whether a resolver method named {@code getX} or {@code isX} (followed by an upper-case letter) also binds
     * the field {@code x}, besides the field named like the method. It applies to the methods of root and type
     * resolvers that do not declare a coordinate with {@code @GraphQLSchemaCoordinate}, and it is decided once at
     * startup. A class where both {@code author} and {@code getAuthor} bind the field {@code author} fails the startup.
     * The compile-time checks read the processor option of the same name. Default: {@code false}.
     *
     * @param resolverAccessorPrefixes whether the prefixes are matched
     */
    public void setResolverAccessorPrefixes(boolean resolverAccessorPrefixes) {
        this.resolverAccessorPrefixes = resolverAccessorPrefixes;
    }

    /**
     * @return the naming convention that derives the class of an object type, or {@code null} for none
     */
    @Nullable
    public String getTypeClassConvention() {
        return typeClassConvention;
    }

    /**
     * Sets the naming convention that derives the class of an object type that a union or an interface needs and the
     * {@code SchemaMappingDictionary} does not name: the simple name of the class is the convention with {@code {Type}}
     * replaced by the name of the object type, for example {@code {Type}Graph} for {@code BookGraph}. The class must be
     * introspected, and a class that declares a name with {@code @GraphQLType(name)} is matched by that name only. It
     * is decided once at startup. The compile-time checks read the processor option {@code micronaut.graphql.tools.processor.type.class.convention}.
     * Default: none.
     *
     * @param typeClassConvention a pattern with {@code {Type}} exactly once, or {@code null}
     */
    public void setTypeClassConvention(@Nullable String typeClassConvention) {
        this.typeClassConvention = typeClassConvention;
    }

    /**
     * @return the severity of each check by its code, as written: {@code fail}, {@code warn} or {@code ignore}
     */
    @NonNull
    public Map<String, CheckSeverity> getChecks() {
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
    public void setChecks(@NonNull Map<String, CheckSeverity> checks) {
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
     * @return the options of the execution of operations, under {@value #PREFIX}.execution
     */
    @NonNull
    public ExecutionConfiguration getExecution() {
        return execution;
    }

    /**
     * @param execution the options of the execution of operations
     */
    public void setExecution(@NonNull ExecutionConfiguration execution) {
        ArgumentUtils.requireNonNull("execution", execution);
        this.execution = execution;
    }

    /**
     * @return the options of error handling, under {@value #PREFIX}.errors
     */
    @NonNull
    public ErrorsConfiguration getErrors() {
        return errors;
    }

    /**
     * @param errors the options of error handling
     */
    public void setErrors(@NonNull ErrorsConfiguration errors) {
        ArgumentUtils.requireNonNull("errors", errors);
        this.errors = errors;
    }

    /**
     * @return the options that protect the server from expensive operations, under {@value #PREFIX}.protection
     */
    @NonNull
    public ProtectionConfiguration getProtection() {
        return protection;
    }

    /**
     * @param protection the options that protect the server from expensive operations
     */
    public void setProtection(@NonNull ProtectionConfiguration protection) {
        ArgumentUtils.requireNonNull("protection", protection);
        this.protection = protection;
    }

    /**
     * Options of the handling of the exceptions that resolvers throw, under
     * {@value GraphQLToolsConfiguration#PREFIX}{@code .errors}.
     */
    @ConfigurationProperties("errors")
    public static class ErrorsConfiguration {

        private boolean details;
        private boolean stackTraces;

        /**
         * @return whether the message of an exception without a mapper is sent to the client
         */
        public boolean isDetails() {
            return details;
        }

        /**
         * Sets whether the client sees the message and the class of an exception that has no
         * {@link io.micronaut.graphql.tools.error.GraphQLExceptionMapper} and is not a {@code GraphQLError}: the error
         * then has the message of the exception and the extension {@code exceptionClass}. Off, the client gets the generic
         * error {@code Internal server error} (code {@code INTERNAL_ERROR}) and the details stay in the log. Turn it
         * on in development and test environments only, for example in {@code application-dev.yml}. Default: {@code false}.
         *
         * @param details whether to send the details
         */
        public void setDetails(boolean details) {
            this.details = details;
        }

        /**
         * @return whether the log entry of an exception without a mapper has the stack trace
         */
        public boolean isStackTraces() {
            return stackTraces;
        }

        /**
         * Sets whether the one log entry of an exception without a mapper (logged at ERROR by
         * {@code io.micronaut.graphql.tools.internal.error.LibraryDataFetcherExceptionHandler}) has the stack trace.
         * Off, it has the path of the field and the exception as one line. Default: {@code false}.
         *
         * @param stackTraces whether to log the stack trace
         */
        public void setStackTraces(boolean stackTraces) {
            this.stackTraces = stackTraces;
        }

    }

    /**
     * Options that protect the server from expensive operations, under
     * {@value GraphQLToolsConfiguration#PREFIX}{@code .protection}. They apply to the {@code GraphQL} bean of the library;
     * an application that builds its own {@code GraphQL} sets its own limits.
     */
    @ConfigurationProperties("protection")
    public static class ProtectionConfiguration {

        /** The default of {@link #getDocumentCacheSize()}. */
        public static final int DEFAULT_DOCUMENT_CACHE_SIZE = 1000;

        private int documentCacheSize = DEFAULT_DOCUMENT_CACHE_SIZE;
        private int maxDepth;
        private int maxComplexity;
        private boolean introspection = true;

        /**
         * @return the number of parsed and validated documents that are kept, {@code 0} for none
         */
        public int getDocumentCacheSize() {
            return documentCacheSize;
        }

        /**
         * Sets how many parsed and validated documents the library keeps, keyed by the text of the query. A query text
         * that is in the cache is neither parsed nor validated again; the least recently used document goes when the cache is
         * full, so the memory is bounded by this many documents. Variables and the operation name are not part of the key.
         * {@code 0} turns the cache off. Default: {@code 1000}.
         *
         * @param documentCacheSize the maximum number of documents
         */
        public void setDocumentCacheSize(int documentCacheSize) {
            if (documentCacheSize < 0) {
                throw new IllegalArgumentException(PREFIX + ".protection.document-cache-size must not be negative: " + documentCacheSize);
            }
            this.documentCacheSize = documentCacheSize;
        }

        /**
         * @return the deepest selection nesting that an operation may have, {@code 0} for no limit
         */
        public int getMaxDepth() {
            return maxDepth;
        }

        /**
         * Sets the deepest selection nesting that an operation may have. An operation over the limit is rejected before
         * any resolver runs, with a GraphQL error. Introspection queries nest deeply, so a low limit rejects them too.
         * {@code 0} means no limit. Default: {@code 0}.
         *
         * @param maxDepth the maximum depth
         */
        public void setMaxDepth(int maxDepth) {
            if (maxDepth < 0) {
                throw new IllegalArgumentException(PREFIX + ".protection.max-depth must not be negative: " + maxDepth);
            }
            this.maxDepth = maxDepth;
        }

        /**
         * @return the highest complexity that an operation may have, {@code 0} for no limit
         */
        public int getMaxComplexity() {
            return maxComplexity;
        }

        /**
         * Sets the highest complexity that an operation may have, where every field that the operation selects counts
         * {@code 1}. An operation over the limit is rejected before any resolver runs, with a GraphQL error. {@code 0} means
         * no limit. Default: {@code 0}.
         *
         * @param maxComplexity the maximum complexity
         */
        public void setMaxComplexity(int maxComplexity) {
            if (maxComplexity < 0) {
                throw new IllegalArgumentException(PREFIX + ".protection.max-complexity must not be negative: " + maxComplexity);
            }
            this.maxComplexity = maxComplexity;
        }

        /**
         * @return whether {@code __schema} and {@code __type} can be queried
         */
        public boolean isIntrospection() {
            return introspection;
        }

        /**
         * Sets whether clients can query {@code __schema} and {@code __type}. Off, such a query is rejected by validation
         * with a GraphQL error, and the schema is not listed to the client; {@code __typename} keeps working. Turning it
         * off replaces any field visibility that the application set on the schema. Default: {@code true}.
         *
         * @param introspection whether introspection is available
         */
        public void setIntrospection(boolean introspection) {
            this.introspection = introspection;
        }

    }

    /**
     * Options of the schema files, under {@value GraphQLToolsConfiguration#PREFIX}{@code .schema}.
     */
    @ConfigurationProperties("schema")
    public static class SchemaConfiguration {

        /** The default of {@link #getLocations()}: every {@code .graphqls} file under {@code graphql} on the classpath. */
        public static final String DEFAULT_SCHEMA_LOCATION = "classpath:graphql/**/*.graphqls";

        private List<String> locations = List.of(DEFAULT_SCHEMA_LOCATION);

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
     * Options of the execution of operations, under {@value GraphQLToolsConfiguration#PREFIX}{@code .execution}.
     */
    @ConfigurationProperties("execution")
    public static class ExecutionConfiguration {

        /** The default of {@link #getExecutor()}: Micronaut's {@code blocking} executor. */
        public static final String DEFAULT_EXECUTOR = "blocking";

        /** The value of {@link #getExecutor()} that runs the operation on the thread that receives it. */
        public static final String NO_EXECUTOR = "none";

        private String executor = DEFAULT_EXECUTOR;
        private boolean dataLoaderStatistics;
        @Nullable
        private Duration timeout;

        /**
         * @return how long an operation may take until its result is ready, or {@code null} for no limit
         */
        @Nullable
        public Duration getTimeout() {
            return timeout;
        }

        /**
         * Sets how long an operation may take, from the moment it arrives until graphql-java has its result. When the time
         * is up, the execution is marked cancelled, so that a later async field completes with an
         * {@link graphql.execution.AbortExecutionException}, and the client gets an error with the code
         * {@code EXECUTION_TIMEOUT} at once. Synchronous code that is running is not interrupted. A subscription is
         * covered until its stream is established, not for the events. The timer is scheduled only when this is set.
         * A duration such as {@code 5s}. Default: none.
         *
         * @param timeout the duration, which must be positive, or {@code null} for no limit
         */
        public void setTimeout(@Nullable Duration timeout) {
            if (timeout != null && (timeout.isNegative() || timeout.isZero())) {
                throw new IllegalArgumentException(PREFIX + ".execution.timeout must be positive: " + timeout);
            }
            this.timeout = timeout;
        }

        /**
         * @return whether the batches of each batch loader bean are counted
         */
        public boolean isDataLoaderStatistics() {
            return dataLoaderStatistics;
        }

        /**
         * Sets whether the library counts the batches that each batch loader bean dispatches in an execution and the keys
         * they load, which {@link io.micronaut.graphql.tools.execution.DataLoaderBatchStatistics} reports from the GraphQL
         * context and the Micrometer module turns into metrics. Counting adds two atomic increments to each batch, so the
         * default is off. Default: {@code false}.
         *
         * @param dataLoaderStatistics whether to collect statistics
         */
        public void setDataLoaderStatistics(boolean dataLoaderStatistics) {
            this.dataLoaderStatistics = dataLoaderStatistics;
        }

        /**
         * @return the executor that runs every operation: an executor bean name or {@code none}
         */
        @NonNull
        public String getExecutor() {
            return executor;
        }

        /**
         * Sets the executor that runs every operation. The operation hops to it once, after the
         * {@code GraphQLExecutionInputCustomizer} has answered and before graphql-java parses the query, so parsing,
         * validation and every synchronous resolver run on it. The customizer does not: it runs on the subscribing
         * thread, which is the HTTP event loop for an HTTP request, so it must not block. {@code blocking} (Micronaut's blocking executor: virtual threads on JDK 21 and later, the
         * {@code io} pool on JDK 17) is the default; {@code none} runs the operation on the thread that receives it,
         * which is the Netty event loop over HTTP and WebSocket; {@code io}, {@code virtual} (JDK 21 and later) or the
         * name of an executor configured under {@code micronaut.executors} choose that executor. {@code scheduled} is
         * rejected. An unknown name fails the startup with {@code unknown-executor}. Default: {@code blocking}.
         *
         * @param executor the executor bean name, or {@code none}
         */
        public void setExecutor(@NonNull String executor) {
            ArgumentUtils.requireNonNull("executor", executor);
            this.executor = executor.strip();
        }

    }

    /**
     * Options of the binding manifest, under {@value GraphQLToolsConfiguration#PREFIX}{@code .manifest}.
     */
    @ConfigurationProperties("manifest")
    public static class ManifestConfiguration {

        private boolean enabled;
        @Nullable
        private String path;

        /**
         * @return whether the binding manifest is recorded while the schema is bound
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Sets whether the binding manifest is recorded while the schema is bound, so that the
         * {@link io.micronaut.graphql.tools.manifest.BindingManifest} bean can be read. It is also written at startup
         * when {@link #getPath()} is set; without a path nothing is written or logged.
         * Default: {@code false}: nothing is recorded, the bean cannot be created. The checks run the same either way: the manifest only lists what they find.
         *
         * @param enabled whether to record and write it
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * @return the file the binding manifest is written to at startup, or {@code null} to write none
         */
        @Nullable
        public String getPath() {
            return path;
        }

        /**
         * Sets the file the binding manifest is written to at startup; its directories are created. Default: none,
         * nothing is written. It has an effect only with {@link #isEnabled()}.
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
         * @return the directives that a {@link io.micronaut.graphql.tools.customizer.GraphQLSchemaCustomizer} handles where graphql-java calls no wiring
         */
        @NonNull
        public List<String> getSchemaCustomizerDirectives() {
            return schemaCustomizerDirectives;
        }

        /**
         * Sets the directives (names without {@code @}) that a {@link io.micronaut.graphql.tools.customizer.GraphQLSchemaCustomizer} handles on the schema
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
     * type resolver method (of the type or of one of its interfaces) can bind it. The rule is the same for object and
     * interface fields. A field that carries a directive listed in {@link #getResolverDirectives()}, or whose type
     * resolver method declares its coordinate with {@code @GraphQLSchemaCoordinate}, is always bound by the type
     * resolver method, and a member marked {@code @GraphQLIgnore} binds nothing.
     */
    public enum Precedence {

        /**
         * Fail the startup with a {@link io.micronaut.graphql.tools.exceptions.SchemaBindingException} of code
         * {@code precedence-ambiguity}, naming the field and both members. {@code strict} does not relax it.
         */
        FAIL,

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
