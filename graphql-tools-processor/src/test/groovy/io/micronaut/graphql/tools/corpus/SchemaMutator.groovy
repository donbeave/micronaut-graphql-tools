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
package io.micronaut.graphql.tools.corpus

import graphql.language.Definition
import graphql.language.Document
import graphql.language.EnumTypeDefinition
import graphql.language.EnumTypeExtensionDefinition
import graphql.language.EnumValueDefinition
import graphql.language.FieldDefinition
import graphql.language.InputObjectTypeDefinition
import graphql.language.InputObjectTypeExtensionDefinition
import graphql.language.InputValueDefinition
import graphql.language.InterfaceTypeDefinition
import graphql.language.InterfaceTypeExtensionDefinition
import graphql.language.ListType
import graphql.language.NonNullType
import graphql.language.ObjectTypeDefinition
import graphql.language.ObjectTypeExtensionDefinition
import graphql.language.Type
import graphql.language.TypeName
import graphql.parser.Parser
import graphql.language.AstPrinter
import groovy.transform.CompileStatic


/**
 * The mutator of the drift mutation tests (#89): it changes the SDL of a case and nothing else. {@link #mutants} lists, for a set of
 * schema files, the <b>breaking</b> mutants (rename a field, change nullability, change list wrapping, drop an enum value, retype a
 * scalar, add an input property that nothing maps) and the <b>equivalent</b> ones (move a field into an extension, reverse the arguments
 * of a field, shuffle the order of the definitions inside a file, of the files, of the extensions). The list is a function of the files
 * and the seed: the same input gives the same mutants in the same order.
 *
 * <p>The files are parsed and printed again with graphql-java, so a mutated file is the printed form of the changed document (comments
 * are lost, which no verdict depends on). A file that the mutator does not change is the original text.</p>
 */
@CompileStatic
final class SchemaMutator {

    static final Map<String, String> RETYPE = [String: 'Boolean', Int: 'String', Float: 'String', Boolean: 'Int', ID: 'Boolean']

    private SchemaMutator() {
    }

    /**
     * @param files the schema files of a case by file name
     * @param seed  seeds the shuffles
     * @return every mutant of the files, breaking ones first, in a stable order
     */
    static List<Mutant> mutants(Map<String, String> files, long seed) {
        Map<String, Document> documents = new LinkedHashMap<>()
        files.each { String name, String text -> documents.put(name, new Parser().parseDocument(text)) }
        List<Mutant> result = []
        documents.each { String file, Document document ->
            document.definitions.eachWithIndex { Definition definition, int at ->
                if (definition instanceof ObjectTypeDefinition) {
                    objectMutants(((ObjectTypeDefinition) definition).name, ((ObjectTypeDefinition) definition).fieldDefinitions, file, document, at, files,
                            result) { int index, FieldDefinition field -> replaceField(document, at, (ObjectTypeDefinition) definition, index, field) }
                } else if (definition instanceof InterfaceTypeDefinition) {
                    objectMutants(((InterfaceTypeDefinition) definition).name, ((InterfaceTypeDefinition) definition).fieldDefinitions, file, document, at,
                            files, result, true) { int index, FieldDefinition field -> replaceInterfaceField(document, at, (InterfaceTypeDefinition) definition, index, field) }
                } else if (definition instanceof InputObjectTypeDefinition) {
                    inputMutants((InputObjectTypeDefinition) definition, file, document, at, files, result)
                } else if (definition instanceof EnumTypeDefinition) {
                    enumMutants((EnumTypeDefinition) definition, file, document, at, files, result)
                }
            }
        }
        result.addAll(equivalents(files, documents, seed))
        return result
    }

    // ---------------------------------------------------------------------------------------------- breaking mutants

    private static void objectMutants(String owner, List<FieldDefinition> fields, String file, Document document, int at, Map<String, String> files,
                                      List<Mutant> out, boolean ofInterface = false, Closure<Document> replace) {
        String element = ofInterface ? owner : null
        fields.eachWithIndex { FieldDefinition field, int i ->
            if (field.name.startsWith('__')) {
                return
            }
            String coordinate = owner + '.' + field.name
            String capitalized = field.name.capitalize()
            List<String> names = [coordinate, '#' + field.name + '(', '#get' + capitalized + '(', '#is' + capitalized + '(']
            if (ofInterface) {
                names.add('.' + field.name)
            }
            String renamed = field.name + 'Renamed'
            out.add(breaking('rename-field', coordinate, names + [owner + '.' + renamed], files, file,
                    replace.call(i, field.transform { FieldDefinition.Builder b -> b.name(renamed) }), element))
            typeMutations(field.type).each { String kind, Type<?> changed ->
                out.add(breaking(kind, coordinate, names, files, file,
                        replace.call(i, field.transform { FieldDefinition.Builder b -> b.type(changed) }), element))
            }
            List<InputValueDefinition> arguments = field.inputValueDefinitions
            arguments.eachWithIndex { InputValueDefinition argument, int k ->
                typeMutations(argument.type).each { String kind, Type<?> changed ->
                    List<InputValueDefinition> copy = new ArrayList<>(arguments)
                    copy.set(k, argument.transform { InputValueDefinition.Builder b -> b.type(changed) })
                    out.add(breaking(kind + '-argument', coordinate + '(' + argument.name + ':)', names, files, file,
                            replace.call(i, field.transform { FieldDefinition.Builder b -> b.inputValueDefinitions(copy) }), element))
                }
            }
        }
    }

    private static void inputMutants(InputObjectTypeDefinition type, String file, Document document, int at, Map<String, String> files, List<Mutant> out) {
        String owner = type.name
        List<InputValueDefinition> fields = type.inputValueDefinitions
        fields.eachWithIndex { InputValueDefinition field, int i ->
            String coordinate = owner + '.' + field.name
            typeMutations(field.type).each { String kind, Type<?> changed ->
                List<InputValueDefinition> copy = new ArrayList<>(fields)
                copy.set(i, field.transform { InputValueDefinition.Builder b -> b.type(changed) })
                out.add(breaking(kind + '-input-field', coordinate, [coordinate], files, file, replaceInput(document, at, type, copy)))
            }
        }
        if (type instanceof InputObjectTypeExtensionDefinition) {
            return
        }
        List<InputValueDefinition> copy = new ArrayList<>(fields)
        copy.add(InputValueDefinition.newInputValueDefinition().name('mutatedExtra').type(TypeName.newTypeName('String').build()).build())
        out.add(breaking('add-unmapped-input-property', owner + '.mutatedExtra', [owner + '.mutatedExtra'], files, file,
                replaceInput(document, at, type, copy), owner))
    }

    private static void enumMutants(EnumTypeDefinition type, String file, Document document, int at, Map<String, String> files, List<Mutant> out) {
        List<EnumValueDefinition> values = type.enumValueDefinitions
        if (values.size() < 2) {
            return
        }
        values.eachWithIndex { EnumValueDefinition value, int i ->
            List<EnumValueDefinition> copy = new ArrayList<>(values)
            copy.remove(i)
            String coordinate = type.name + '.' + value.name
            out.add(breaking('drop-enum-value', coordinate, [coordinate, '#' + value.name], files, file, replaceEnum(document, at, type, copy)))
        }
    }

    /** the changes of a type that a mutant makes, by the name of the mutation */
    private static Map<String, Type<?>> typeMutations(Type<?> type) {
        Map<String, Type<?>> result = new LinkedHashMap<>()
        result.put('toggle-nullability', type instanceof NonNullType ? ((NonNullType) type).type : NonNullType.newNonNullType(type).build())
        result.put('change-list-wrapping', toggleList(type))
        String base = baseName(type)
        if (RETYPE.containsKey(base)) {
            result.put('retype-scalar', rename(type, RETYPE.get(base)))
        }
        return result
    }

    private static Type<?> toggleList(Type<?> type) {
        if (type instanceof ListType) {
            Type<?> element = ((ListType) type).type
            return element instanceof NonNullType ? ((NonNullType) element).type : element
        }
        if (type instanceof NonNullType && ((NonNullType) type).type instanceof ListType) {
            Type<?> element = ((ListType) ((NonNullType) type).type).type
            return element instanceof NonNullType ? element : NonNullType.newNonNullType(element).build()
        }
        if (type instanceof NonNullType) {
            return NonNullType.newNonNullType(ListType.newListType(((NonNullType) type).type).build()).build()
        }
        return ListType.newListType(type).build()
    }

    private static String baseName(Type<?> type) {
        if (type instanceof NonNullType) {
            return baseName(((NonNullType) type).type)
        }
        if (type instanceof ListType) {
            return baseName(((ListType) type).type)
        }
        return ((TypeName) type).name
    }

    private static Type<?> rename(Type<?> type, String name) {
        if (type instanceof NonNullType) {
            return NonNullType.newNonNullType(rename(((NonNullType) type).type, name)).build()
        }
        if (type instanceof ListType) {
            return ListType.newListType(rename(((ListType) type).type, name)).build()
        }
        return TypeName.newTypeName(name).build()
    }

    private static Mutant breaking(String kind, String coordinate, List<String> names, Map<String, String> files, String file, Document changed,
                                   String element = null) {
        Map<String, String> copy = new LinkedHashMap<>(files)
        copy.put(file, AstPrinter.printAst(changed))
        return new Mutant(kind, true, coordinate, names, copy, element)
    }

    // -------------------------------------------------------------------------------------------- equivalent mutants

    private static List<Mutant> equivalents(Map<String, String> files, Map<String, Document> documents, long seed) {
        List<Mutant> result = []
        documents.each { String file, Document document ->
            document.definitions.eachWithIndex { Definition definition, int at ->
                if (definition instanceof ObjectTypeDefinition && !(definition instanceof ObjectTypeExtensionDefinition)) {
                    ObjectTypeDefinition type = (ObjectTypeDefinition) definition
                    if (type.fieldDefinitions.size() >= 2) {
                        FieldDefinition moved = type.fieldDefinitions.last()
                        List<Definition> definitions = new ArrayList<>(document.definitions)
                        definitions.set(at, type.transform { ObjectTypeDefinition.Builder b -> b.fieldDefinitions(type.fieldDefinitions.dropRight(1)) })
                        definitions.add(ObjectTypeExtensionDefinition.newObjectTypeExtensionDefinition().name(type.name).fieldDefinition(moved).build())
                        result.add(equivalent('move-field-to-extension', type.name + '.' + moved.name, files, file, withDefinitions(document, definitions)))
                    }
                    type.fieldDefinitions.eachWithIndex { FieldDefinition field, int i ->
                        if (field.inputValueDefinitions.size() >= 2) {
                            List<InputValueDefinition> reversed = field.inputValueDefinitions.reverse()
                            result.add(equivalent('reorder-arguments', type.name + '.' + field.name, files, file,
                                    replaceField(document, at, type, i, field.transform { FieldDefinition.Builder b -> b.inputValueDefinitions(reversed) })))
                        }
                    }
                }
            }
            if (document.definitions.size() >= 2) {
                List<Definition> shuffled = new ArrayList<>(document.definitions)
                Collections.shuffle(shuffled, new Random(seed ^ file.hashCode()))
                if (shuffled == document.definitions) {
                    Collections.reverse(shuffled)
                }
                if (shuffled != document.definitions) {
                    result.add(equivalent('shuffle-definitions', file, files, file, withDefinitions(document, shuffled)))
                }
                List<Definition> extensionsFirst = document.definitions.findAll { it instanceof ObjectTypeExtensionDefinition } +
                        document.definitions.findAll { !(it instanceof ObjectTypeExtensionDefinition) }
                if (extensionsFirst != document.definitions && document.definitions.any { it instanceof ObjectTypeExtensionDefinition }) {
                    result.add(equivalent('extensions-first', file, files, file, withDefinitions(document, extensionsFirst)))
                }
            }
        }
        if (files.size() >= 2) {
            Map<String, String> reversed = new LinkedHashMap<>()
            List<String> names = new ArrayList<>(files.keySet())
            names.eachWithIndex { String name, int i -> reversed.put(String.format('%02d-%s', names.size() - i, name), files.get(name)) }
            result.add(new Mutant('shuffle-files', false, 'files', [], reversed))
            List<Definition> all = documents.values().collectMany { it.definitions }
            Collections.shuffle(all, new Random(seed))
            List<List<Definition>> original = documents.values().collect { it.definitions }
            List<List<Definition>> parts = distribute(all, names.size())
            if (parts == original) {
                Collections.rotate(all, 1)
                parts = distribute(all, names.size())
            }
            Map<String, String> spread = new LinkedHashMap<>()
            names.eachWithIndex { String name, int i ->
                List<Definition> part = parts.get(i)
                spread.put(name, AstPrinter.printAst(documents.get(name).transform { Document.Builder b -> b.definitions(part) }))
            }
            result.add(new Mutant('shuffle-across-files', false, 'files', [], spread))
        }
        return result
    }

    private static List<List<Definition>> distribute(List<Definition> definitions, int count) {
        List<List<Definition>> parts = (0..<count).collect { (List<Definition>) new ArrayList<Definition>() }
        definitions.eachWithIndex { Definition definition, int i -> parts.get(i % count).add(definition) }
        return parts
    }

    private static Mutant equivalent(String kind, String coordinate, Map<String, String> files, String file, Document changed) {
        Map<String, String> copy = new LinkedHashMap<>(files)
        copy.put(file, AstPrinter.printAst(changed))
        return new Mutant(kind, false, coordinate, [], copy)
    }

    // ------------------------------------------------------------------------------------------------ AST editing

    private static Document withDefinitions(Document document, List<Definition> definitions) {
        return document.transform { Document.Builder b -> b.definitions(definitions) }
    }

    private static Document replaceDefinition(Document document, int at, Definition definition) {
        List<Definition> definitions = new ArrayList<>(document.definitions)
        definitions.set(at, definition)
        return withDefinitions(document, definitions)
    }

    private static Document replaceField(Document document, int at, ObjectTypeDefinition type, int index, FieldDefinition field) {
        List<FieldDefinition> fields = new ArrayList<>(type.fieldDefinitions)
        fields.set(index, field)
        ObjectTypeDefinition changed = type instanceof ObjectTypeExtensionDefinition
                ? ((ObjectTypeExtensionDefinition) type).transformExtension { ObjectTypeExtensionDefinition.Builder b -> b.fieldDefinitions(fields) }
                : type.transform { ObjectTypeDefinition.Builder b -> b.fieldDefinitions(fields) }
        return replaceDefinition(document, at, changed)
    }

    private static Document replaceInterfaceField(Document document, int at, InterfaceTypeDefinition type, int index, FieldDefinition field) {
        List<FieldDefinition> fields = new ArrayList<>(type.fieldDefinitions)
        fields.set(index, field)
        InterfaceTypeDefinition changed = type instanceof InterfaceTypeExtensionDefinition
                ? ((InterfaceTypeExtensionDefinition) type).transformExtension { InterfaceTypeExtensionDefinition.Builder b -> b.definitions(fields) }
                : type.transform { InterfaceTypeDefinition.Builder b -> b.definitions(fields) }
        return replaceDefinition(document, at, changed)
    }

    private static Document replaceInput(Document document, int at, InputObjectTypeDefinition type, List<InputValueDefinition> fields) {
        InputObjectTypeDefinition changed = type instanceof InputObjectTypeExtensionDefinition
                ? ((InputObjectTypeExtensionDefinition) type).transformExtension { InputObjectTypeExtensionDefinition.Builder b -> b.inputValueDefinitions(fields) }
                : type.transform { InputObjectTypeDefinition.Builder b -> b.inputValueDefinitions(fields) }
        return replaceDefinition(document, at, changed)
    }

    private static Document replaceEnum(Document document, int at, EnumTypeDefinition type, List<EnumValueDefinition> values) {
        EnumTypeDefinition changed = type instanceof EnumTypeExtensionDefinition
                ? ((EnumTypeExtensionDefinition) type).transformExtension { EnumTypeExtensionDefinition.Builder b -> b.enumValueDefinitions(values) }
                : type.transform { EnumTypeDefinition.Builder b -> b.enumValueDefinitions(values) }
        return replaceDefinition(document, at, changed)
    }

}
