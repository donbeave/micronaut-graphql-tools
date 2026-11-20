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
package io.micronaut.graphql.tools.scalars.internal.json;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.ArrayValue;
import graphql.language.BooleanValue;
import graphql.language.EnumValue;
import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.language.VariableReference;
import graphql.schema.CoercingParseValueException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.scalars.internal.WireCoercing;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonNode;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAccumulator;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;
import java.util.RandomAccess;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * The JSON scalar. A value that is already JSON (a string, a number, a boolean, a map, a collection) is passed on as it is,
 * which allocates nothing. Any other value, such as a bean or a record, is converted with the application's {@link JsonMapper}
 * (Micronaut Serialization in an application that uses it) into maps, lists and primitives.
 */
@Internal
public final class JsonCoercing extends WireCoercing<Object, Object> {

    private static final String NOT_FINITE = "a number that is not finite cannot be written as JSON";

    private static final BiConsumer<Object, Object> OUTPUT_ENTRY = (key, element) -> checkOutput(element);

    private static final BiConsumer<Object, Object> INPUT_ENTRY = (key, element) -> checkInput(element);

    private final Supplier<JsonMapper> mapper;

    /**
     * @param scalarName the name of the scalar in the schema
     * @param mapper     the mapper that converts a value that is not JSON yet; asked only when there is such a value
     */
    public JsonCoercing(String scalarName, Supplier<JsonMapper> mapper) {
        super(scalarName);
        this.mapper = mapper;
    }

    @Override
    protected Object write(Object value) {
        if (isJson(value)) {
            checkOutput(value);
            return value;
        }
        try {
            Object converted = toJava(mapper.get().writeValueToTree(value));
            checkOutput(converted);
            return converted;
        } catch (IOException e) {
            throw new IllegalArgumentException("the value cannot be written as JSON", e);
        }
    }

    private static void checkOutput(Object value) {
        if (value instanceof Double) {
            if (!Double.isFinite((Double) value)) {
                throw new IllegalArgumentException(NOT_FINITE);
            }
        } else if (value instanceof Float) {
            if (!Float.isFinite((Float) value)) {
                throw new IllegalArgumentException(NOT_FINITE);
            }
        } else if (value instanceof Map) {
            ((Map<?, ?>) value).forEach((BiConsumer<Object, Object>) OUTPUT_ENTRY);
        } else if (value instanceof List && value instanceof RandomAccess) {
            List<?> list = (List<?>) value;
            for (int i = 0, size = list.size(); i < size; i++) {
                checkOutput(list.get(i));
            }
        } else if (value instanceof Collection) {
            for (Object element : (Collection<?>) value) {
                checkOutput(element);
            }
        }
    }

    private static void checkInput(Object value) {
        if (value instanceof Double) {
            if (!Double.isFinite((Double) value)) {
                throw new IllegalArgumentException(NOT_FINITE);
            }
        } else if (value instanceof Float) {
            if (!Float.isFinite((Float) value)) {
                throw new IllegalArgumentException(NOT_FINITE);
            }
        } else if (value instanceof BigDecimal) {
            checkDecimal((BigDecimal) value);
        } else if (value instanceof Map) {
            ((Map<?, ?>) value).forEach((BiConsumer<Object, Object>) INPUT_ENTRY);
        } else if (value instanceof List && value instanceof RandomAccess) {
            List<?> list = (List<?>) value;
            for (int i = 0, size = list.size(); i < size; i++) {
                checkInput(list.get(i));
            }
        } else if (value instanceof Collection) {
            for (Object element : (Collection<?>) value) {
                checkInput(element);
            }
        }
    }

    private static double checkDecimal(BigDecimal decimal) {
        double number = decimal.doubleValue();
        if (Double.isInfinite(number)) {
            throw new IllegalArgumentException("a number out of the range of a JSON number");
        }
        if (number == 0.0d && decimal.signum() != 0) {
            throw new IllegalArgumentException("a number too close to zero for a JSON number");
        }
        return number;
    }

    @Override
    public Value<?> valueToLiteral(Object input, GraphQLContext graphQLContext, Locale locale) {
        try {
            return literal(isJson(input) ? input : write(input));
        } catch (RuntimeException e) {
            throw new CoercingParseValueException(scalarName() + ": " + e.getMessage(), e);
        }
    }

    private static Value<?> literal(Object value) {
        if (value == null) {
            return NullValue.of();
        }
        if (value instanceof CharSequence) {
            return StringValue.of(value.toString());
        }
        if (value instanceof Boolean) {
            return BooleanValue.of((Boolean) value);
        }
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            return IntValue.newIntValue(BigInteger.valueOf(((Number) value).longValue())).build();
        }
        if (value instanceof BigInteger) {
            return IntValue.newIntValue((BigInteger) value).build();
        }
        if (value instanceof BigDecimal) {
            return FloatValue.newFloatValue((BigDecimal) value).build();
        }
        if (value instanceof AtomicInteger || value instanceof AtomicLong || value instanceof LongAdder
                || value instanceof LongAccumulator) {
            return IntValue.newIntValue(BigInteger.valueOf(((Number) value).longValue())).build();
        }
        if (value instanceof Number) {
            return number((Number) value);
        }
        if (value instanceof Map) {
            ObjectValue.Builder object = ObjectValue.newObjectValue();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                object.objectField(ObjectField.newObjectField().name(String.valueOf(entry.getKey()))
                        .value(literal(entry.getValue())).build());
            }
            return object.build();
        }
        ArrayValue.Builder array = ArrayValue.newArrayValue();
        for (Object element : (Collection<?>) value) {
            array.value(literal(element));
        }
        return array.build();
    }

    private static Value<?> number(Number value) {
        double number = value.doubleValue();
        if (Double.isNaN(number) || Double.isInfinite(number)) {
            throw new IllegalArgumentException(NOT_FINITE);
        }
        if (value instanceof Float || value instanceof Double || value instanceof DoubleAdder
                || value instanceof DoubleAccumulator) {
            return FloatValue.newFloatValue(BigDecimal.valueOf(number)).build();
        }
        try {
            return FloatValue.newFloatValue(new BigDecimal(value.toString())).build();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("a number of the kind " + value.getClass().getName()
                    + " has no exact decimal form", e);
        }
    }

    @Override
    protected Object read(Object input) {
        checkInput(input);
        return input;
    }

    @Override
    protected Object readLiteral(Value<?> literal, CoercedVariables variables) {
        if (literal instanceof NullValue) {
            return null;
        }
        if (literal instanceof StringValue) {
            return ((StringValue) literal).getValue();
        }
        if (literal instanceof EnumValue) {
            return ((EnumValue) literal).getName();
        }
        if (literal instanceof BooleanValue) {
            return ((BooleanValue) literal).isValue();
        }
        if (literal instanceof IntValue) {
            return integer(((IntValue) literal).getValue());
        }
        if (literal instanceof FloatValue) {
            return checkDecimal(((FloatValue) literal).getValue());
        }
        if (literal instanceof VariableReference) {
            return variables.get(((VariableReference) literal).getName());
        }
        if (literal instanceof ArrayValue) {
            List<Object> list = new ArrayList<>();
            for (Value<?> element : ((ArrayValue) literal).getValues()) {
                list.add(readLiteral(element, variables));
            }
            return list;
        }
        if (literal instanceof ObjectValue) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (ObjectField field : ((ObjectValue) literal).getObjectFields()) {
                map.put(field.getName(), readLiteral(field.getValue(), variables));
            }
            return map;
        }
        throw new IllegalArgumentException("expected a JSON value, but was " + describeLiteral(literal));
    }

    private static boolean isJson(Object value) {
        return value == null || value instanceof CharSequence || value instanceof Number || value instanceof Boolean
                || value instanceof Map || value instanceof Collection;
    }

    private static Object integer(BigInteger value) {
        if (value.bitLength() < Integer.SIZE) {
            return value.intValue();
        }
        if (value.bitLength() < Long.SIZE) {
            return value.longValue();
        }
        return value;
    }

    private static Object toJava(JsonNode node) {
        if (node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> entry : node.entries()) {
                map.put(entry.getKey(), toJava(entry.getValue()));
            }
            return map;
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>(node.size());
            for (JsonNode element : node.values()) {
                list.add(toJava(element));
            }
            return list;
        }
        if (node.isNumber()) {
            return node.getNumberValue();
        }
        if (node.isBoolean()) {
            return node.getBooleanValue();
        }
        return node.getStringValue();
    }

}
