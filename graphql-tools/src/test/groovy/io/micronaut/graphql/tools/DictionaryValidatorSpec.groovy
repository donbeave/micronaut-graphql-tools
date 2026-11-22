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
package io.micronaut.graphql.tools

import io.micronaut.graphql.tools.mapping.SchemaMappingDictionary
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import spock.lang.Specification
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.graphql.tools.binding.BindingCode
import io.micronaut.graphql.tools.internal.checks.DictionaryValidator

/**
 * The dictionary check reports what it always reported: the pairs of an entry mapped to an interface and an entry mapped
 * to a class that implements it, in the order of the sorted entries, whatever the size of the dictionary.
 */
class DictionaryValidatorSpec extends Specification {

    static final List<Class<?>> CLASSES = [
            Runnable, Comparable, CharSequence, Collection, List, Iterable, ArrayList, LinkedList, String, StringBuilder,
            Integer, Number, Object, Map, HashMap, TreeMap, Set, HashSet,
    ]

    void "report the same conflicts, in the same order, as the comparison of every pair of entries"() {
        given:
        Random random = new Random(seed)
        Map<String, Class<?>> entries = new HashMap<>()
        // a class is mapped to one GraphQL type only
        List<Class<?>> classes = new ArrayList<>(CLASSES)
        Collections.shuffle(classes, random)
        classes.take(random.nextInt(CLASSES.size() - 1) + 2).each { entries.put("T" + random.nextInt(60), it) }
        TypeDefinitionRegistry registry = new SchemaParser().parse(
                entries.keySet().collect { "type $it { id: ID }" }.join("\n") + "\ntype Query { id: ID }")
        SchemaMappingDictionary dictionary = new SchemaMappingDictionary()
        entries.each { name, clazz -> dictionary.registerType(name, clazz) }

        when:
        SchemaBindingException thrown = null
        try {
            new DictionaryValidator(registry, dictionary).validate()
        } catch (SchemaBindingException e) {
            assert e.getCode() == BindingCode.AMBIGUOUS_TYPE_MAPPING
            thrown = e
        }
        Map<String, Object> expected = everyPair(entries)

        then:
        if (expected.conflicts.isEmpty()) {
            thrown == null
        } else {
            thrown.message.contains("\n  " + expected.conflicts.join("\n  ") + "\nAn object")
            thrown.graphQlTypes == expected.types.toList().sort()
        }

        where:
        seed << (1..300)
    }

    // the comparison the check was before: every pair, the entry that sorts first read as the interface first
    private static Map<String, Object> everyPair(Map<String, Class<?>> entries) {
        List<Map.Entry<String, Class<?>>> sorted = entries.entrySet().sort { it.key }
        List<String> conflicts = []
        Set<String> types = new TreeSet<>()
        for (int i = 0; i < sorted.size(); i++) {
            for (int j = i + 1; j < sorted.size(); j++) {
                String conflict = describe(sorted[i], sorted[j]) ?: describe(sorted[j], sorted[i])
                if (conflict != null) {
                    conflicts << conflict
                    types << sorted[i].key << sorted[j].key
                }
            }
        }
        return [conflicts: conflicts, types: types]
    }

    private static String describe(Map.Entry<String, Class<?>> iface, Map.Entry<String, Class<?>> other) {
        if (!iface.value.isInterface() || !iface.value.isAssignableFrom(other.value)) {
            return null
        }
        return "`" + iface.key + "` is mapped to the interface " + iface.value.name + " and `" + other.key + "` to " +
                other.value.name + ", which implements it"
    }

}
