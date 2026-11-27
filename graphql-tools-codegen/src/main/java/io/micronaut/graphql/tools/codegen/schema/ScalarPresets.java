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
package io.micronaut.graphql.tools.codegen.schema;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * The Java classes that the generator uses for the scalars that the library registers at runtime, so that a schema that declares
 * one of them needs no {@code typeMappings} entry and the generated class matches the runtime mapping. A preset applies to a
 * scalar only, never to an object, enum or input type of the same name, and an entry of {@code typeMappings} wins over it. The
 * presets of the scalars module follow its {@code StandardScalar}, and a test fails when they differ.
 */
public final class ScalarPresets {

    /** The qualified name of the class that the {@code Upload} scalar of the upload module is bound to. */
    public static final String UPLOAD_CLASS = "io.micronaut.http.multipart.CompletedFileUpload";

    private static final Map<String, String> PRESETS;

    static {
        Map<String, String> presets = new TreeMap<>();
        presets.put("Long", "java.lang.Long");
        presets.put("Short", "java.lang.Short");
        presets.put("BigDecimal", "java.math.BigDecimal");
        presets.put("BigInteger", "java.math.BigInteger");
        presets.put("JSON", "java.lang.Object");
        presets.put("Upload", UPLOAD_CLASS);
        presets.put("Instant", "java.time.Instant");
        presets.put("LocalDate", "java.time.LocalDate");
        presets.put("LocalTime", "java.time.LocalTime");
        presets.put("LocalDateTime", "java.time.LocalDateTime");
        presets.put("OffsetTime", "java.time.OffsetTime");
        presets.put("OffsetDateTime", "java.time.OffsetDateTime");
        presets.put("ZonedDateTime", "java.time.ZonedDateTime");
        presets.put("YearMonth", "java.time.YearMonth");
        presets.put("Year", "java.time.Year");
        presets.put("Duration", "java.time.Duration");
        presets.put("Period", "java.time.Period");
        presets.put("Url", "java.net.URL");
        presets.put("UUID", "java.util.UUID");
        presets.put("Locale", "java.util.Locale");
        presets.put("Currency", "java.util.Currency");
        for (String name : new String[] {"PositiveInt", "NegativeInt", "NonPositiveInt", "NonNegativeInt"}) {
            presets.put(name, "java.lang.Integer");
        }
        for (String name : new String[] {"PositiveFloat", "NegativeFloat", "NonPositiveFloat", "NonNegativeFloat"}) {
            presets.put(name, "java.lang.Double");
        }
        PRESETS = Collections.unmodifiableMap(presets);
    }

    private ScalarPresets() {
    }

    /**
     * The presets.
     *
     * @return the qualified class name of each scalar name, in the order of the names
     */
    public static Map<String, String> presets() {
        return PRESETS;
    }
}
