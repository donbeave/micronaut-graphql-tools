/*
 * Copyright 2021-2022 original authors
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
import java.util.stream.Collectors;

/**
 * The checks of {@link GraphQLRuntimeWiringGenerator} that compare input classes with what the input
 * {@code ObjectMapper} can set and with their GraphQL input types. Findings are collected while the input types are
 * bound and reported by {@link #checkCollected}. It logs under the generator's name.
 */
@Internal
final class InputClassChecks {

    private static final Logger LOG = LoggerFactory.getLogger(GraphQLRuntimeWiringGenerator.class);

    private final InputValueConverter inputValueConverter;
    private final Map<Class<?>, InputValueConverter.OmissionPlan> omissionPlans;
    private final List<Map.Entry<InputMappingContext, Class<?>>> writeOnlyInputObjectProperties = new ArrayList<>();
    private final Map<String, Map.Entry<Class<?>, List<String>>> unmappedInputProperties = new TreeMap<>();
    private final Map<String, Class<?>> ownDeserializers = new TreeMap<>();

    /**
     * @param inputValueConverter the converter whose {@code ObjectMapper} converts requests
     * @param omissionPlans       the omission plans of the input classes, filled while they are bound
     */
    InputClassChecks(InputValueConverter inputValueConverter,
                     Map<Class<?>, InputValueConverter.OmissionPlan> omissionPlans) {
        this.inputValueConverter = inputValueConverter;
        this.omissionPlans = omissionPlans;
    }

    /**
     * Records a write-only property that holds an input object, to check once all omission plans are known.
     *
     * @param mappingContext the input field
     * @param inputClass     the input class the property holds
     */
    void addWriteOnlyInputObjectProperty(InputMappingContext mappingContext, Class<?> inputClass) {
        writeOnlyInputObjectProperties.add(Map.entry(mappingContext, inputClass));
    }

    /**
     * Records an input class that the input {@code ObjectMapper} converts with a deserializer of its own, so its input
     * fields cannot be checked for settable properties; {@link #checkCollected} logs them.
     *
     * @param inputTypeName the GraphQL input type
     * @param inputClass    the input class
     */
    void addOwnDeserializer(String inputTypeName, Class<?> inputClass) {
        ownDeserializers.put(inputTypeName, inputClass);
    }

    /**
     * Fails on the findings collected while the input types were bound. Each unmapped input class property is an
     * {@code !unbound} line of the binding manifest, also when it does not fail.
     *
     * @param failOnUnmappedProperties whether unmapped input class properties fail
     * @param manifestRecorder         the binding manifest
     */
    void checkCollected(boolean failOnUnmappedProperties, BindingManifestRecorder manifestRecorder) {
        checkWriteOnlyInputObjectProperties();

        if (!ownDeserializers.isEmpty()) {
            LOG.info("Input classes converted by a deserializer of their own are not checked for settable input "
                    + "fields: {}", ownDeserializers.entrySet().stream()
                    .map(it -> it.getKey() + " (" + it.getValue().getName() + ")")
                    .collect(Collectors.joining(", ")));
        }

        unmappedInputProperties.values().forEach(entry -> entry.getValue().forEach(property ->
                manifestRecorder.diagnostic("unbound", entry.getKey().getTypeName() + "#" + property)));

        if (failOnUnmappedProperties && !unmappedInputProperties.isEmpty()) {
            throw new UnmappedInputPropertiesException(unmappedInputProperties);
        }
    }

    /**
     * Asks the input {@code ObjectMapper} which properties of an input class it can set, by building the deserializer
     * that converts requests: properties with a setter, a field or a creator parameter, under the names Jackson uses
     * (after naming strategies and {@code @JsonProperty}), without ignored ones. An input field that is not among them
     * would fail every request that sends it, or be dropped by a lenient mapper.
     *
     * @param inputClass the input class
     * @return the names, or {@code null} if the mapper uses a deserializer of its own for the class (then the
     * application controls the conversion and the check is not possible)
     */
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

    /**
     * A write-only property of an input object type cannot be visited after a conversion, so the
     * {@link ArgumentValue} properties further down could not be set to {@code omitted()}.
     */
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

    /**
     * Records the properties of an input class that a request can set (through a setter or the constructor) but that
     * the GraphQL input type does not define. Read-only properties, such as computed getters, are not input.
     *
     * @param inputObjectTypeDefinition the input type
     * @param beanIntrospection         the introspection of its input class
     */
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
