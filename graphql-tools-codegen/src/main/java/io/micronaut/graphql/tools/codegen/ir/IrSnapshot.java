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
package io.micronaut.graphql.tools.codegen.ir;

import io.micronaut.graphql.tools.codegen.ir.shared.AppliedDirective;
import io.micronaut.graphql.tools.codegen.ir.shared.Docs;
import io.micronaut.graphql.tools.codegen.ir.shared.Names;
import io.micronaut.graphql.tools.codegen.ir.shared.TypeRef;
import io.micronaut.graphql.tools.codegen.ir.shared.Wrapper;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Prints an {@link Ir} as text, the form that the golden tests compare. The text has one block for each node in the order of the
 * IR, uses {@code \n} line endings and ends with one newline, so the same IR always prints to the same text. A line holds a name
 * and its generated class, its nullability as the GraphQL {@code !}, its origin as {@code path:line:column}, and its
 * documentation. The text is for people to read and diff and is not a format to parse.
 */
public final class IrSnapshot {

    private IrSnapshot() {
    }

    /**
     * Prints an IR.
     *
     * @param ir the IR
     * @return the text
     */
    public static String print(Ir ir) {
        StringBuilder out = new StringBuilder();
        ir.scalars().forEach(it -> scalar(out, it));
        ir.enums().forEach(it -> enumType(out, it));
        ir.inputs().forEach(it -> input(out, it));
        ir.abstractTypes().forEach(it -> abstractType(out, it));
        ir.models().forEach(it -> model(out, it));
        ir.contracts().forEach(it -> contract(out, it));
        ir.nameClasses().forEach(it -> nameClass(out, it));
        return out.toString();
    }

    private static void nameClass(StringBuilder out, NameClass nameClass) {
        out.append("names ").append(nameClass.packageName()).append('.').append(nameClass.simpleName()).append('\n');
        for (NameConstant constant : nameClass.constants()) {
            out.append("  constant ").append(constant.name()).append(" = ").append(constant.value()).append(" @ ")
                    .append(constant.origin()).append('\n');
        }
    }

    private static void scalar(StringBuilder out, ScalarType scalar) {
        out.append("scalar ").append(scalar.names().sdlName()).append(" -> ").append(scalar.className()).append(" @ ")
                .append(scalar.origin()).append('\n');
        details(out, "  ", scalar.docs(), scalar.directives());
    }

    private static void enumType(StringBuilder out, EnumType type) {
        out.append("enum ").append(names(type.names())).append(" @ ").append(type.origin()).append('\n');
        details(out, "  ", type.docs(), type.directives());
        for (EnumValue value : type.values()) {
            out.append("  value ").append(name(value.sdlName(), value.name())).append(" @ ").append(value.origin()).append('\n');
            details(out, "    ", value.docs(), value.directives());
        }
    }

    private static void input(StringBuilder out, InputType type) {
        out.append("input ").append(names(type.names())).append(" @ ").append(type.origin()).append('\n');
        details(out, "  ", type.docs(), type.directives());
        for (InputField field : type.fields()) {
            out.append("  field ").append(name(field.sdlName(), field.name())).append(": ").append(type(field.type()))
                    .append(defaultValue(field.defaultValue())).append(" @ ").append(field.origin()).append('\n');
            details(out, "    ", field.docs(), field.directives());
        }
    }

    private static void abstractType(StringBuilder out, AbstractType type) {
        out.append(type.kind().name().toLowerCase(Locale.ROOT)).append(' ').append(names(type.names()))
                .append(type.sealed() ? " sealed" : " open").append(" @ ").append(type.origin()).append('\n');
        details(out, "  ", type.docs(), type.directives());
        list(out, "  implements ", type.implemented());
        list(out, "  members ", type.members());
        fields(out, type.fields());
    }

    private static void model(StringBuilder out, ModelType type) {
        out.append("model ").append(names(type.names())).append(" @ ").append(type.origin()).append('\n');
        details(out, "  ", type.docs(), type.directives());
        list(out, "  implements ", type.implemented());
        list(out, "  memberOf ", type.memberOf());
        fields(out, type.fields());
    }

    private static void contract(StringBuilder out, ResolverContract contract) {
        out.append("contract ").append(names(contract.names())).append(" : ").append(contract.marker()).append(" @ ")
                .append(contract.origin()).append('\n');
        for (ContractMethod method : contract.methods()) {
            out.append("  method ").append(name(method.coordinate().fieldName(), method.name())).append(" returns ")
                    .append(type(method.returns())).append(" wrapper ").append(wrapper(method.wrapper())).append(" @ ")
                    .append(method.origin()).append('\n');
            details(out, "    ", method.docs(), method.directives());
            arguments(out, method.arguments());
        }
    }

    private static void fields(StringBuilder out, List<Field> fields) {
        for (Field field : fields) {
            out.append("  field ").append(name(field.sdlName(), field.name())).append(": ").append(type(field.type()))
                    .append(field.boxed() ? " boxed" : "").append(" @ ").append(field.origin()).append('\n');
            details(out, "    ", field.docs(), field.directives());
            arguments(out, field.arguments());
        }
    }

    private static void arguments(StringBuilder out, List<Argument> arguments) {
        for (Argument argument : arguments) {
            out.append("    argument ").append(name(argument.sdlName(), argument.name())).append(": ")
                    .append(type(argument.type())).append(defaultValue(argument.defaultValue())).append(" @ ")
                    .append(argument.origin()).append('\n');
            details(out, "      ", argument.docs(), argument.directives());
        }
    }

    private static void details(StringBuilder out, String indent, Docs docs, List<AppliedDirective> directives) {
        if (docs.description() != null) {
            out.append(indent).append("description ").append(quote(docs.description())).append('\n');
        }
        if (docs.deprecation() != null) {
            out.append(indent).append("deprecated ").append(quote(docs.deprecation().reason())).append('\n');
        }
        for (AppliedDirective directive : directives) {
            out.append(indent).append('@').append(directive.name());
            if (!directive.arguments().isEmpty()) {
                out.append('(');
                boolean first = true;
                for (Map.Entry<String, String> argument : directive.arguments().entrySet()) {
                    out.append(first ? "" : ", ").append(argument.getKey()).append(": ").append(quote(argument.getValue()));
                    first = false;
                }
                out.append(')');
            }
            out.append('\n');
        }
    }

    private static void list(StringBuilder out, String label, List<Names> names) {
        if (!names.isEmpty()) {
            out.append(label);
            for (int i = 0; i < names.size(); i++) {
                out.append(i == 0 ? "" : ", ").append(names.get(i).sdlName());
            }
            out.append('\n');
        }
    }

    private static String names(Names names) {
        return names.sdlName() + " -> " + names.qualifiedName();
    }

    private static String name(String sdlName, String name) {
        return sdlName.equals(name) ? sdlName : sdlName + " -> " + name;
    }

    private static String defaultValue(String value) {
        return value == null ? "" : " = " + value;
    }

    private static String wrapper(Wrapper wrapper) {
        return wrapper.shape() == Wrapper.Shape.GENERIC ? wrapper.className() + "<T>" : wrapper.shape().name().toLowerCase(Locale.ROOT);
    }

    private static String type(TypeRef type) {
        String text;
        if (type instanceof TypeRef.Builtin builtin) {
            text = switch (builtin.scalar()) {
                case ID -> "ID";
                case STRING -> "String";
                case INT -> "Int";
                case FLOAT -> "Float";
                case BOOLEAN -> "Boolean";
            };
        } else if (type instanceof TypeRef.Mapped mapped) {
            text = "mapped(" + mapped.className() + ")";
        } else if (type instanceof TypeRef.Reference reference) {
            text = reference.target().sdlName();
        } else {
            text = "[" + type(((TypeRef.ListOf) type).element()) + "]";
        }
        return type.isNullable() ? text : text + "!";
    }

    private static String quote(String text) {
        StringBuilder quoted = new StringBuilder(text.length() + 2).append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\n' -> quoted.append("\\n");
                default -> quoted.append(c);
            }
        }
        return quoted.append('"').toString();
    }
}
