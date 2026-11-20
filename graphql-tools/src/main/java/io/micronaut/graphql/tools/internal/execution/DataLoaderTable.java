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
package io.micronaut.graphql.tools.internal.execution;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.internal.exceptions.configuration.ExecutionSetupException;
import io.micronaut.inject.BeanDefinition;
import jakarta.inject.Singleton;
import org.dataloader.BatchLoader;
import org.dataloader.BatchLoaderWithContext;
import org.dataloader.MappedBatchLoader;
import org.dataloader.MappedBatchLoaderWithContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The batch loader beans of the application, found once at startup from the bean definitions (compile-time metadata, no
 * reflection): each has a name and an index, and the key and value types of its generic type arguments. The per-execution
 * registry creates a {@code DataLoader} from an entry on first use.
 */
@Internal
@Singleton
@Requires(property = "graphql.enabled", notEquals = "false")
public final class DataLoaderTable {

    private final BeanContext beanContext;
    private final Entry[] entries;
    private final Map<String, Entry> byName;

    DataLoaderTable(BeanContext beanContext) {
        this.beanContext = beanContext;
        Map<BeanDefinition<?>, Kind> kinds = new LinkedHashMap<>();
        // a bean that implements several interfaces is served as the first of them, in this order
        for (Kind kind : new Kind[] {Kind.MAPPED_WITH_CONTEXT, Kind.MAPPED, Kind.BATCH_WITH_CONTEXT, Kind.BATCH}) {
            for (BeanDefinition<?> definition : beanContext.getBeanDefinitions(kind.type)) {
                kinds.putIfAbsent(definition, kind);
            }
        }
        List<Entry> list = new ArrayList<>(kinds.size());
        Map<String, Entry> names = new HashMap<>();
        for (Map.Entry<BeanDefinition<?>, Kind> found : kinds.entrySet()) {
            BeanDefinition<?> definition = found.getKey();
            Kind kind = found.getValue();
            List<Argument<?>> arguments = definition.getTypeArguments(kind.type);
            Argument<?> key = arguments.size() == 2 ? arguments.get(0) : Argument.OBJECT_ARGUMENT;
            Argument<?> value = arguments.size() == 2 ? arguments.get(1) : Argument.OBJECT_ARGUMENT;
            Entry entry = new Entry(list.size(), nameOf(definition), kind, definition, key, value);
            Entry other = names.putIfAbsent(entry.name(), entry);
            if (other != null) {
                throw new ExecutionSetupException(BindingCode.DUPLICATE_DATA_LOADER, "Two batch loader beans are named '" + entry.name()
                        + "': " + other.definition().getBeanType().getName() + " and " + definition.getBeanType().getName()
                        + ". The name is the @Named qualifier of the bean, or else the simple class name with a lower-case first "
                        + "letter. Fix: give one of them a different @Named qualifier.");
            }
            list.add(entry);
        }
        this.entries = list.toArray(new Entry[0]);
        this.byName = Map.copyOf(names);
    }

    /** @return whether the application has no batch loader bean */
    public boolean isEmpty() {
        return entries.length == 0;
    }

    Entry entry(int index) {
        return entries[index];
    }

    Entry entry(String name) {
        return byName.get(name);
    }

    BeanContext beanContext() {
        return beanContext;
    }

    /**
     * Picks the loader that a resolver parameter {@code DataLoader<K, V>} gets.
     *
     * @param name     the {@code @Named} value of the parameter, or null
     * @param key      the key type of the parameter
     * @param value    the value type of the parameter
     * @param location where the parameter is, for the message
     * @return the index of the loader
     * @throws ExecutionSetupException with code {@code data-loader-not-found}
     */
    public int resolve(String name, Argument<?> key, Argument<?> value, String location) {
        if (name != null) {
            Entry entry = byName.get(name);
            if (entry == null) {
                throw new ExecutionSetupException(BindingCode.DATA_LOADER_NOT_FOUND, location + " is named '" + name
                        + "', and no batch loader bean has that name. " + known());
            }
            if (!matches(entry, key, value)) {
                throw new ExecutionSetupException(BindingCode.DATA_LOADER_NOT_FOUND, location + " is DataLoader<" + typeName(key)
                        + ", " + typeName(value) + "> and is named '" + name + "', but that loader is " + entry.describe()
                        + ". Fix: declare the parameter with the types of the loader.");
            }
            return entry.index();
        }
        Entry found = null;
        List<String> several = new ArrayList<>();
        for (Entry entry : entries) {
            if (matches(entry, key, value)) {
                if (found == null) {
                    found = entry;
                }
                several.add(entry.describe());
            }
        }
        if (found == null) {
            throw new ExecutionSetupException(BindingCode.DATA_LOADER_NOT_FOUND, location + " is DataLoader<" + typeName(key)
                    + ", " + typeName(value) + ">, and no batch loader bean has these key and value types. " + known()
                    + " Fix: register a BatchLoader, BatchLoaderWithContext, MappedBatchLoader or MappedBatchLoaderWithContext bean "
                    + "with these types.");
        }
        if (several.size() > 1) {
            throw new ExecutionSetupException(BindingCode.DATA_LOADER_NOT_FOUND, location + " is DataLoader<" + typeName(key)
                    + ", " + typeName(value) + ">, and " + several.size() + " batch loader beans have these key and value types: "
                    + String.join("; ", several) + ". Fix: name the loader with @Named(\"<name>\") on the parameter.");
        }
        return found.index();
    }

    private String known() {
        if (entries.length == 0) {
            return "The application has no batch loader bean.";
        }
        List<String> described = new ArrayList<>();
        for (Entry entry : entries) {
            described.add(entry.describe());
        }
        return "The batch loader beans are: " + String.join("; ", described) + ".";
    }

    private static boolean matches(Entry entry, Argument<?> key, Argument<?> value) {
        return typeName(entry.key()).equals(typeName(key)) && typeName(entry.value()).equals(typeName(value));
    }

    private static String typeName(Argument<?> argument) {
        return argument.getTypeString(false);
    }

    private static String nameOf(BeanDefinition<?> definition) {
        String named = definition.getAnnotationMetadata().stringValue(AnnotationUtil.NAMED).orElse(null);
        if (named != null && !named.isEmpty()) {
            return named;
        }
        String simple = definition.getBeanType().getSimpleName();
        return Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
    }

    /** The batch loader interface that a bean implements. */
    enum Kind {
        BATCH(BatchLoader.class),
        BATCH_WITH_CONTEXT(BatchLoaderWithContext.class),
        MAPPED(MappedBatchLoader.class),
        MAPPED_WITH_CONTEXT(MappedBatchLoaderWithContext.class);

        private final Class<?> type;

        Kind(Class<?> type) {
            this.type = type;
        }

        boolean mapped() {
            return this == MAPPED || this == MAPPED_WITH_CONTEXT;
        }
    }

    /**
     * One batch loader bean.
     *
     * @param index      the position in the table
     * @param name       the name of the loader in the {@code DataLoaderRegistry}
     * @param kind       the batch loader interface of the bean
     * @param definition the bean definition
     * @param key        the key type
     * @param value      the value type
     */
    record Entry(int index, String name, Kind kind, BeanDefinition<?> definition, Argument<?> key, Argument<?> value) {

        /** @return the loader as a description for a message: its name and types */
        String describe() {
            return name + " (" + definition.getBeanType().getName() + ", DataLoader<" + typeName(key) + ", " + typeName(value) + ">)";
        }
    }

}
