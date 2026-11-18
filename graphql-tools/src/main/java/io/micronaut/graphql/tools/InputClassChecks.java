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

import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBase;
import com.fasterxml.jackson.databind.deser.DefaultDeserializationContext;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.type.Argument;
import io.micronaut.graphql.tools.annotation.GraphQLIgnore;
import io.micronaut.graphql.tools.exceptions.UnmappedInputPropertiesException;
import io.micronaut.graphql.tools.exceptions.UnsupportedInputTypeException;
import io.micronaut.graphql.tools.schema.InputValueConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Internal
final class InputClassChecks {

    private static final Logger LOG = LoggerFactory.getLogger(GraphQLRuntimeWiringGenerator.class);

    private final InputValueConverter inputValueConverter;
    private final Map<Class<?>, InputValueConverter.OmissionPlan> omissionPlans;
    private final List<Map.Entry<InputMappingContext, Class<?>>> writeOnlyInputObjectProperties = new ArrayList<>();
    private final Map<String, Map.Entry<Class<?>, List<String>>> unmappedInputProperties = new TreeMap<>();
    private final Map<String, Class<?>> ownDeserializers = new TreeMap<>();
    private final Set<String> ignoredInputProperties = new TreeSet<>();

    InputClassChecks(InputValueConverter inputValueConverter,
                     Map<Class<?>, InputValueConverter.OmissionPlan> omissionPlans) {
        this.inputValueConverter = inputValueConverter;
        this.omissionPlans = omissionPlans;
    }

    void addWriteOnlyInputObjectProperty(InputMappingContext mappingContext, Class<?> inputClass) {
        writeOnlyInputObjectProperties.add(Map.entry(mappingContext, inputClass));
    }

    void addOwnDeserializer(String inputTypeName, Class<?> inputClass) {
        ownDeserializers.put(inputTypeName, inputClass);
    }

    void checkCollected(boolean failOnUnmappedProperties, BindingManifestRecorder manifestRecorder) {
        checkWriteOnlyInputObjectProperties();

        if (!ownDeserializers.isEmpty()) {
            LOG.info("Input classes converted by a deserializer of their own are not checked for settable input "
                    + "fields: {}", ownDeserializers.entrySet().stream()
                    .map(it -> it.getKey() + " (" + it.getValue().getName() + ")")
                    .collect(Collectors.joining(", ")));
        }

        ignoredInputProperties.forEach(manifestRecorder::ignored);

        unmappedInputProperties.values().forEach(entry -> entry.getValue().forEach(property ->
                manifestRecorder.diagnostic("unbound", entry.getKey().getTypeName() + "#" + property)));

        if (failOnUnmappedProperties && !unmappedInputProperties.isEmpty()) {
            throw new UnmappedInputPropertiesException(unmappedInputProperties);
        }
    }

    @Nullable
    Set<String> findSettableProperties(Class<?> inputClass) {
        ObjectMapper objectMapper = inputValueConverter.getObjectMapper();
        DeserializationConfig config = objectMapper.getDeserializationConfig();
        JsonDeserializer<Object> deserializer;
        try {
            deserializer = ((DefaultDeserializationContext) objectMapper.getDeserializationContext())
                    .createDummyInstance(config)
                    .findRootValueDeserializer(objectMapper.constructType(inputClass));
        } catch (JsonMappingException e) {
            throw new IllegalStateException("The input ObjectMapper cannot convert into the input class "
                    + inputClass.getName() + ": " + e.getOriginalMessage(), e);
        }
        if (!(deserializer instanceof BeanDeserializerBase beanDeserializer)) {
            return null;
        }
        Set<String> result = new HashSet<>();
        beanDeserializer.properties().forEachRemaining(it -> result.add(it.getName()));
        beanDeserializer.creatorProperties().forEachRemaining(it -> result.add(it.getName()));
        return result;
    }

    static Class<?> elementClass(Argument<?> argument) {
        Argument<?> result = argument;
        while ((ArgumentValue.class.equals(result.getType()) || Iterable.class.isAssignableFrom(result.getType()))
                && result.getFirstTypeVariable().isPresent()) {
            result = result.getFirstTypeVariable().get();
        }
        return result.getType();
    }

    private void checkWriteOnlyInputObjectProperties() {
        for (Map.Entry<InputMappingContext, Class<?>> entry : writeOnlyInputObjectProperties) {
            if (holdsArgumentValues(entry.getValue(), new HashSet<>())) {
                throw new UnsupportedInputTypeException(entry.getKey(), "the property is write-only, but its input "
                        + "class " + entry.getValue().getName() + " has " + ArgumentValue.class.getName()
                        + " properties, which are read to set them to omitted() when the client leaves them out.");
            }
        }
    }

    private boolean holdsArgumentValues(Class<?> inputClass, Set<Class<?>> visited) {
        InputValueConverter.OmissionPlan plan = omissionPlans.get(inputClass);
        if (plan == null || !visited.add(inputClass)) {
            return false;
        }
        if (!plan.argumentValueProperties().isEmpty()) {
            return true;
        }
        return plan.inputObjectProperties().stream()
                .anyMatch(it -> holdsArgumentValues(elementClass(it.asArgument()), visited));
    }

    void collectUnmappedInputProperties(InputObjectTypeDefinition inputObjectTypeDefinition,
                                        BeanIntrospection<Object> beanIntrospection) {
        Set<String> inputFields = inputObjectTypeDefinition.getInputValueDefinitions().stream()
                .map(InputValueDefinition::getName)
                .collect(Collectors.toSet());
        Set<String> constructorArguments = Arrays.stream(beanIntrospection.getConstructorArguments())
                .map(Argument::getName)
                .collect(Collectors.toSet());
        List<String> unmapped = beanIntrospection.getBeanProperties().stream()
                .filter(it -> !it.isReadOnly() || constructorArguments.contains(it.getName()))
                .filter(it -> {
                    if (!it.hasAnnotation(GraphQLIgnore.class) || inputFields.contains(it.getName())) {
                        return true;
                    }
                    ignoredInputProperties.add(beanIntrospection.getBeanType().getTypeName() + "#" + it.getName());
                    return false;
                })
                .map(BeanProperty::getName)
                .filter(it -> !inputFields.contains(it))
                .sorted()
                .toList();
        if (!unmapped.isEmpty()) {
            unmappedInputProperties.put(inputObjectTypeDefinition.getName(),
                    Map.entry(beanIntrospection.getBeanType(), unmapped));
        }
    }

}
