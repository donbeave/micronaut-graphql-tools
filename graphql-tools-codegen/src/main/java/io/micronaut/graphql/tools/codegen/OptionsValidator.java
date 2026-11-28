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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The rules of {@link Options#validate()}. Every finding names the option it is about.
 */
final class OptionsValidator {

    private static final Pattern GRAPHQL_NAME = Pattern.compile("[_A-Za-z][_0-9A-Za-z]*");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]*)}");
    private static final List<String> PLACEHOLDERS = List.of("Field", "Operation");

    private final Options options;
    private final List<Diagnostic> found = new ArrayList<>();

    private OptionsValidator(Options options) {
        this.options = options;
    }

    static List<Diagnostic> validate(Options options) {
        OptionsValidator validator = new OptionsValidator(options);
        validator.check();
        return List.copyOf(validator.found);
    }

    private void invalid(String option, String message) {
        found.add(Diagnostic.optionError(Diagnostic.INVALID_OPTION_VALUE, option, message));
    }

    private void check() {
        if (options.packageName().isEmpty()) {
            found.add(Diagnostic.optionError(Diagnostic.MISSING_OPTION, "packageName", "The option packageName is required."));
        } else {
            qualifiedName("packageName", options.packageName());
        }
        optionalQualifiedName("modelPackage", options.modelPackage());
        optionalQualifiedName("resolverPackage", options.resolverPackage());
        optionalQualifiedName("namesPackage", options.namesPackage());
        optionalQualifiedName("generatedAnnotation", options.generatedAnnotation());
        optionalQualifiedName("nullableAnnotation", options.nullableAnnotation());
        qualifiedName("subscriptionReturnType", options.subscriptionReturnType());
        affix("typeNamePrefix", options.typeNamePrefix(), true);
        affix("typeNameSuffix", options.typeNameSuffix(), false);
        for (int i = 0; i < options.interfaceTypes().size(); i++) {
            String name = options.interfaceTypes().get(i);
            if (!GRAPHQL_NAME.matcher(name).matches()) {
                invalid("interfaceTypes." + i, "The option interfaceTypes." + i + " must be an SDL type name, not '" + name + "'.");
            }
        }
        contractNameTemplate();
        for (Map.Entry<String, String> mapping : options.typeMappings().entrySet()) {
            typeMapping(mapping.getKey(), mapping.getValue());
        }
        patterns("includes", options.includes());
        patterns("excludes", options.excludes());
        patterns("operationIncludes", options.operationIncludes());
        patterns("operationExcludes", options.operationExcludes());
        if (options.operationIncludes().isEmpty()) {
            invalid("operationIncludes", "The option operationIncludes selects no operation document: it has no glob.");
        }
        if (options.includes().isEmpty()) {
            invalid("includes", "The option includes selects no schema file: it has no glob.");
        }
        if (options.kotlinSuspend() && options.language() != Language.KOTLIN) {
            found.add(Diagnostic.optionError(Diagnostic.INVALID_OPTION_COMBINATION, "kotlinSuspend",
                    "The option kotlinSuspend needs language KOTLIN, not " + options.language() + "."));
        }
        if (options.mode() == Mode.CLIENT && options.language() != Language.JAVA) {
            found.add(Diagnostic.optionError(Diagnostic.INVALID_OPTION_COMBINATION, "mode",
                    "The option mode CLIENT generates Java only, so language must be JAVA, not " + options.language() + "."));
        }
    }

    private void optionalQualifiedName(String option, String value) {
        if (!value.isEmpty()) {
            qualifiedName(option, value);
        }
    }

    private void qualifiedName(String option, String value) {
        if (!JavaNames.isQualifiedName(value)) {
            invalid(option, "The option " + option + " must be a qualified Java name such as com.example.Name, not '" + value + "'.");
        }
    }

    private void affix(String option, String value, boolean prefix) {
        boolean valid = value.isEmpty() || !prefix || Character.isJavaIdentifierStart(value.codePointAt(0));
        if (!valid || !value.codePoints().allMatch(Character::isJavaIdentifierPart)) {
            invalid(option, "The option " + option + " must hold only characters of a Java identifier"
                    + (prefix ? " and start with a letter, '_' or '$'" : "") + ", not '" + value + "'.");
        }
    }

    private void contractNameTemplate() {
        String template = options.contractNameTemplate();
        Matcher matcher = PLACEHOLDER.matcher(template);
        boolean known = true;
        while (matcher.find()) {
            if (!PLACEHOLDERS.contains(matcher.group(1))) {
                known = false;
                invalid("contractNameTemplate", "The option contractNameTemplate has the unknown placeholder {" + matcher.group(1)
                        + "}; the placeholders are " + PLACEHOLDERS.stream().map(p -> "{" + p + "}").toList() + ".");
            }
        }
        if (known && !JavaNames.isIdentifier(PLACEHOLDER.matcher(template).replaceAll("X"))) {
            invalid("contractNameTemplate", "The option contractNameTemplate must give a Java identifier, not '" + template + "'.");
        }
    }

    private void typeMapping(String key, String value) {
        String option = "typeMappings." + key;
        String[] parts = key.split("\\.", -1);
        boolean valid = parts.length <= 2 && Arrays.stream(parts).allMatch(part -> GRAPHQL_NAME.matcher(part).matches());
        if (!valid) {
            invalid(option, "The option " + option + " must have an SDL name or Type.field as its key, not '" + key + "'.");
        }
        if (!JavaNames.isQualifiedName(value)) {
            invalid(option, "The option " + option + " must be a qualified class name, not '" + value + "'.");
        }
    }

    private void patterns(String option, List<String> globs) {
        for (int i = 0; i < globs.size(); i++) {
            if (globs.get(i).isBlank()) {
                invalid(option + "." + i, "The option " + option + "." + i + " must be a glob, not empty.");
            }
        }
    }
}
