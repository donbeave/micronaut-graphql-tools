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

import graphql.language.ArrayValue;
import graphql.language.BooleanValue;
import graphql.language.Comment;
import graphql.language.Description;
import graphql.language.Directive;
import graphql.language.EnumValue;
import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.Node;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.language.VariableReference;
import io.micronaut.graphql.tools.codegen.ir.AppliedDirective;
import io.micronaut.graphql.tools.codegen.ir.Docs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the text of schema nodes into the strings of the IR: escaped documentation, applied directives and values in GraphQL
 * syntax. The documentation is escaped here, once, because neither JavaPoet nor KotlinPoet escapes the text of a comment.
 */
final class NodeText {

    static final String DEPRECATED = "deprecated";
    static final String DEFAULT_DEPRECATION_REASON = "No longer supported";

    private final boolean commentsAsDescriptions;

    NodeText(boolean commentsAsDescriptions) {
        this.commentsAsDescriptions = commentsAsDescriptions;
    }

    Docs docs(Node<?> node, Description description, List<Directive> directives) {
        String text = description(node, description);
        Docs.Deprecation deprecation = null;
        for (Directive directive : directives) {
            if (directive.getName().equals(DEPRECATED)) {
                deprecation = new Docs.Deprecation(escape(reason(directive)));
                break;
            }
        }
        return text == null && deprecation == null ? Docs.NONE : new Docs(text == null ? null : escape(text), deprecation);
    }

    List<AppliedDirective> directives(List<Directive> directives) {
        List<AppliedDirective> applied = new ArrayList<>(directives.size());
        for (Directive directive : directives) {
            if (directive.getName().equals(DEPRECATED)) {
                continue;
            }
            Map<String, String> arguments = new LinkedHashMap<>();
            directive.getArguments().forEach(argument -> arguments.put(argument.getName(), argument.getValue() instanceof StringValue string
                    ? string.getValue() : value(argument.getValue())));
            applied.add(new AppliedDirective(directive.getName(), arguments));
        }
        return applied;
    }

    private String description(Node<?> node, Description description) {
        if (description != null) {
            return description.getContent().isEmpty() ? null : description.getContent();
        }
        if (!commentsAsDescriptions) {
            return null;
        }
        List<String> lines = new ArrayList<>();
        for (Comment comment : node.getComments()) {
            String line = comment.getContent();
            if (line.trim().isEmpty()) {
                lines.clear();
            } else {
                lines.add(line);
            }
        }
        return lines.isEmpty() ? null : String.join("\n", lines);
    }

    private static String reason(Directive directive) {
        var reason = directive.getArgument("reason");
        return reason != null && reason.getValue() instanceof StringValue string ? string.getValue() : DEFAULT_DEPRECATION_REASON;
    }

    static String value(Value<?> value) {
        if (value == null) {
            return null;
        } else if (value instanceof IntValue integer) {
            return integer.getValue().toString();
        } else if (value instanceof FloatValue number) {
            return number.getValue().toString();
        } else if (value instanceof StringValue string) {
            return quote(string.getValue());
        } else if (value instanceof BooleanValue bool) {
            return Boolean.toString(bool.isValue());
        } else if (value instanceof NullValue) {
            return "null";
        } else if (value instanceof EnumValue constant) {
            return constant.getName();
        } else if (value instanceof VariableReference variable) {
            return "$" + variable.getName();
        } else if (value instanceof ArrayValue array) {
            List<String> items = new ArrayList<>(array.getValues().size());
            array.getValues().forEach(item -> items.add(value(item)));
            return "[" + String.join(", ", items) + "]";
        }
        List<String> fields = new ArrayList<>();
        for (ObjectField field : ((ObjectValue) value).getObjectFields()) {
            fields.add(field.getName() + ": " + value(field.getValue()));
        }
        return "{" + String.join(", ", fields) + "}";
    }

    private static String quote(String text) {
        StringBuilder quoted = new StringBuilder(text.length() + 2).append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\n' -> quoted.append("\\n");
                case '\r' -> quoted.append("\\r");
                case '\t' -> quoted.append("\\t");
                default -> quoted.append(c);
            }
        }
        return quoted.append('"').toString();
    }

    /**
     * Escapes text for the body of a Javadoc, KDoc or Groovydoc comment. The comment terminator and the comment opener (Kotlin nests
     * comments), a backslash (a {@code \}{@code u} sequence is read inside comments), {@code <}, {@code &} and {@code @} (a
     * {@code {@}{@code link}} or a block tag) become character references. Line endings become {@code \n}.
     *
     * @param text the text, or {@code null}
     * @return the escaped text
     */
    static String escape(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(text.length() + 16);
        int length = text.length();
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            char next = i + 1 < length ? text.charAt(i + 1) : '\0';
            if (c == '*' && next == '/') {
                out.append("*&#47;");
                i++;
            } else if (c == '/' && next == '*') {
                out.append("/&#42;");
                i++;
            } else if (c == '&') {
                out.append("&amp;");
            } else if (c == '<') {
                out.append("&lt;");
            } else if (c == '@') {
                out.append("&#64;");
            } else if (c == '\\') {
                out.append("&#92;");
            } else if (c == '\r') {
                out.append('\n');
                if (next == '\n') {
                    i++;
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
