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
package probe;

import graphql.language.Document;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.UnExecutableSchemaGenerator;
import graphql.validation.ValidationError;
import graphql.validation.Validator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The measured work, as a compile-time check would do it: parse the schema text and build the schema (cold: the first call in the
 * compiler's JVM), then parse and validate every operation document (the first pass cold, then repeated passes warm). The
 * result is one line appended to the file that the environment variable {@value #OUT} names, one line per compilation.
 */
final class Workload {

    static final String OUT = "LARGE_SCHEMA_OUT";
    private static final int WARM_PASSES = 30;
    private static final Set<String> STANDARD_SCALARS = Set.of("ID", "String", "Int", "Float", "Boolean");
    private static final AtomicBoolean DONE = new AtomicBoolean();

    private Workload() {
    }

    static void runOnce(String host) {
        if (!DONE.compareAndSet(false, true)) {
            return;
        }
        String schemaText = Generator.schema();
        List<String> texts = Generator.documents();

        long t0 = System.nanoTime();
        TypeDefinitionRegistry registry = new SchemaParser().parse(schemaText);
        long t1 = System.nanoTime();
        GraphQLSchema schema = UnExecutableSchemaGenerator.makeUnExecutableSchema(registry);
        long t2 = System.nanoTime();

        int types = (int) schema.getAllTypesAsList().stream().filter(it -> !it.getName().startsWith("__") && !STANDARD_SCALARS.contains(it.getName())).count();
        Validator validator = new Validator();
        long coldParse = 0;
        long coldValidate = 0;
        List<Long> warmParse = new ArrayList<>();
        List<Long> warmValidate = new ArrayList<>();
        int errors = 0;
        for (int pass = 0; pass <= WARM_PASSES; pass++) {
            long p0 = System.nanoTime();
            List<Document> documents = new ArrayList<>(texts.size());
            for (String text : texts) {
                documents.add(Parser.parse(text));
            }
            long p1 = System.nanoTime();
            int passErrors = 0;
            for (Document document : documents) {
                List<ValidationError> found = validator.validateDocument(schema, document, Locale.ENGLISH);
                passErrors += found.size();
            }
            long p2 = System.nanoTime();
            errors = passErrors;
            if (pass == 0) {
                coldParse = p1 - p0;
                coldValidate = p2 - p1;
            } else {
                warmParse.add(p1 - p0);
                warmValidate.add(p2 - p1);
            }
        }
        String line = String.format(Locale.ROOT,
                "host=%s java=%s types=%d documents=%d errors=%d schema-parse-ms=%.1f schema-build-ms=%.1f "
                        + "cold-doc-parse-ms=%.1f cold-validate-ms=%.1f warm-doc-parse-ms=%.1f warm-validate-ms=%.1f",
                host, System.getProperty("java.version"), types,
                texts.size(), errors, ms(t1 - t0), ms(t2 - t1), ms(coldParse), ms(coldValidate), ms(median(warmParse)), ms(median(warmValidate)));
        System.out.println("[large-schema] " + line);
        String out = System.getenv(OUT);
        if (out != null) {
            try {
                Files.writeString(Path.of(out), line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static long median(List<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(null);
        return sorted.get(sorted.size() / 2);
    }

    private static double ms(long nanos) {
        return nanos / 1_000_000.0;
    }
}
