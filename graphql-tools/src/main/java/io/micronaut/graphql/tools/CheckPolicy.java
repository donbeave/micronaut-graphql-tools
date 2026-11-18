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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.value.PropertyResolver;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.binding.internal.finding.DefaultSeverities;
import io.micronaut.graphql.tools.binding.internal.finding.Severity;
import io.micronaut.graphql.tools.exceptions.InvalidConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The severity of every check and the findings that are allowed, resolved and validated once at startup from
 * {@code strict}, {@code checks} and {@code allow.findings}.
 */
@Internal
final class CheckPolicy {

    private static final Logger LOG = LoggerFactory.getLogger(GraphQLRuntimeWiringGenerator.class);
    private static final String PREFIX = GraphQLToolsConfiguration.PREFIX + ".";

    private static final Map<String, String> REMOVED = new LinkedHashMap<>();

    static {
        REMOVED.put("unreachable-types", "checks.unreachable-type and checks.unreachable-value-type (fail, warn or "
                + "ignore; ignore is the old unreachable-types: ignore)");
        REMOVED.put("allow-unreachable", "allow.unreachable");
        REMOVED.put("input.unmapped-properties", "checks.unmapped-input-property");
        REMOVED.put("inert-directives", "allow.inert-directives");
        REMOVED.put("schema-customizer-directives", "allow.schema-customizer-directives");
        REMOVED.put("unwired-directives", "checks.unwired-directive");
    }

    private final Map<BindingCode, Severity> severities = new EnumMap<>(BindingCode.class);
    private final Map<BindingCode, String> origins = new EnumMap<>(BindingCode.class);
    private final Set<String> allowedFindings;
    private final Set<String> matchedFindings = new TreeSet<>();

    private CheckPolicy(boolean strict, Map<BindingCode, Severity> explicit, Set<String> allowedFindings) {
        for (BindingCode code : DefaultSeverities.TABLE.keySet()) {
            Severity set = explicit.get(code);
            severities.put(code, set != null ? set : DefaultSeverities.of(code, strict));
            origins.put(code, set != null ? PREFIX + "checks." + code.code() + " is " + severities.get(code).token()
                    : PREFIX + "strict is " + strict);
        }
        this.allowedFindings = allowedFindings;
    }

    /**
     * @param configuration the configuration
     * @param resolver      the properties, to find removed keys that the configuration class no longer binds
     * @return the validated policy
     * @throws InvalidConfigurationException for a removed key, an unknown check or severity, or an entry of
     *                                       {@code allow.findings} that is not the line of a check
     */
    static CheckPolicy of(GraphQLToolsConfiguration configuration, PropertyResolver resolver) {
        List<String> removed = new ArrayList<>();
        REMOVED.forEach((key, replacement) -> {
            if (resolver.containsProperty(PREFIX + key)) {
                removed.add(PREFIX + key + " was removed; use " + PREFIX + replacement + ".");
            }
        });
        if (!removed.isEmpty()) {
            throw new InvalidConfigurationException(BindingCode.REMOVED_CONFIGURATION_KEY,
                    "The configuration uses keys that were removed in 1.0.0-M3 (BC-23):", removed);
        }

        Map<BindingCode, Severity> explicit = new EnumMap<>(BindingCode.class);
        List<String> problems = new ArrayList<>();
        configuration.getChecks().forEach((key, value) -> {
            String name = NameUtils.hyphenate(key.replace('.', '-').replace('_', '-'));
            BindingCode code = BindingCode.fromCode(name);
            Severity severity = Severity.fromToken(value);
            if (code == null || !code.check()) {
                problems.add(PREFIX + "checks." + key + ": "
                        + (code == null ? "not a code of the registry" : "a failure code, not a check")
                        + "; the checks are " + checkNames() + ".");
            } else if (severity == null) {
                problems.add(PREFIX + "checks." + key + ": \"" + value + "\" is not a severity; use "
                        + Severity.tokens() + ".");
            } else {
                explicit.put(code, severity);
            }
        });
        if (!problems.isEmpty()) {
            throw new InvalidConfigurationException(BindingCode.INVALID_CHECK_SETTING,
                    "The checks setting is invalid:", problems);
        }

        Set<String> allowed = new LinkedHashSet<>();
        List<String> badFindings = new ArrayList<>();
        for (String entry : configuration.getAllow().getFindings()) {
            String line = entry.strip();
            int space = line.indexOf(' ');
            BindingCode code = line.startsWith("!") && space > 1 ? BindingCode.fromCode(line.substring(1, space)) : null;
            if (code == null || !code.check() || line.length() == space + 1) {
                badFindings.add(PREFIX + "allow.findings: \"" + entry + "\" is not a line of the binding manifest "
                        + "that a check writes (!<check> <subject>).");
            } else {
                allowed.add(line);
            }
        }
        if (!badFindings.isEmpty()) {
            throw new InvalidConfigurationException(BindingCode.INVALID_ALLOW_FINDINGS,
                    "The allow.findings setting is invalid:", badFindings);
        }
        CheckPolicy policy = new CheckPolicy(configuration.isStrict(), explicit, allowed);
        List<String> unused = new ArrayList<>();
        for (String line : allowed) {
            BindingCode code = BindingCode.fromCode(line.substring(1, line.indexOf(' ')));
            if (!policy.enabled(code)) {
                unused.add(PREFIX + "allow.findings: \"" + line + "\" has no effect, " + policy.origins.get(code) + ".");
            }
        }
        if (!unused.isEmpty()) {
            throw new InvalidConfigurationException(BindingCode.INVALID_ALLOW_FINDINGS,
                    "The allow.findings setting lists findings of checks that are ignored; remove the entries:", unused);
        }
        return policy;
    }

    private static String checkNames() {
        return DefaultSeverities.TABLE.keySet().stream().map(BindingCode::code).toList().toString();
    }

    Severity severity(BindingCode code) {
        return severities.get(code);
    }

    boolean enabled(BindingCode code) {
        return severities.get(code) != Severity.IGNORE;
    }

    boolean failing(BindingCode code) {
        return severities.get(code) == Severity.FAIL;
    }

    /**
     * @param line a manifest {@code !} line of a finding
     * @return whether {@code allow.findings} lists it; the entry then counts as used
     */
    boolean allowed(String line) {
        if (allowedFindings.contains(line)) {
            matchedFindings.add(line);
            return true;
        }
        return false;
    }

    /**
     * Logs findings of a check at {@code warn}: one warning per check.
     *
     * @param code  the check
     * @param texts the findings
     */
    void warn(BindingCode code, List<String> texts) {
        if (texts.isEmpty() || !LOG.isWarnEnabled()) {
            return;
        }
        StringBuilder builder = new StringBuilder(origins.get(code)).append(", so the startup goes on:");
        texts.forEach(it -> builder.append("\n  ").append(it));
        LOG.warn("{}", code.tag(builder.toString()));
    }

    /**
     * Fails the startup for an {@code allow.findings} entry that matched no finding.
     */
    void verifyAllowedFindings() {
        List<String> stale = new ArrayList<>();
        for (String line : allowedFindings) {
            if (!matchedFindings.contains(line)) {
                stale.add(PREFIX + "allow.findings: \"" + line + "\" matches no finding.");
            }
        }
        if (!stale.isEmpty()) {
            throw new InvalidConfigurationException(BindingCode.INVALID_ALLOW_FINDINGS,
                    "The allow.findings setting lists findings that do not exist; remove the entries:", stale);
        }
    }

}
