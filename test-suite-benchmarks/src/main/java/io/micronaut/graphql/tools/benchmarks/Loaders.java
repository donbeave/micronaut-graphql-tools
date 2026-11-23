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
package io.micronaut.graphql.tools.benchmarks;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Named;
import org.dataloader.BatchLoader;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Batch loader beans that no operation uses, for the gate that says a registered but unused loader costs nothing per
 * request: the {@code benchmarks.loaders} property selects one loader or fifty.
 */
final class Loaders {

    private static final BatchLoader<String, String> KEYS = keys -> CompletableFuture.completedFuture(List.copyOf(keys));

    private Loaders() {
    }

    @Factory
    @Requires(property = "benchmarks.loaders", value = "1")
    static class One {

        @Bean
        @Named("loader0")
        BatchLoader<String, String> loader0() {
            return KEYS;
        }
    }

    @Factory
    @Requires(property = "benchmarks.loaders", value = "50")
    static class Fifty {

        @Bean
        @Named("loader0")
        BatchLoader<String, String> loader0() {
            return KEYS;
        }

        @Bean
        @Named("loader1")
        BatchLoader<String, String> loader1() {
            return KEYS;
        }

        @Bean
        @Named("loader2")
        BatchLoader<String, String> loader2() {
            return KEYS;
        }

        @Bean
        @Named("loader3")
        BatchLoader<String, String> loader3() {
            return KEYS;
        }

        @Bean
        @Named("loader4")
        BatchLoader<String, String> loader4() {
            return KEYS;
        }

        @Bean
        @Named("loader5")
        BatchLoader<String, String> loader5() {
            return KEYS;
        }

        @Bean
        @Named("loader6")
        BatchLoader<String, String> loader6() {
            return KEYS;
        }

        @Bean
        @Named("loader7")
        BatchLoader<String, String> loader7() {
            return KEYS;
        }

        @Bean
        @Named("loader8")
        BatchLoader<String, String> loader8() {
            return KEYS;
        }

        @Bean
        @Named("loader9")
        BatchLoader<String, String> loader9() {
            return KEYS;
        }

        @Bean
        @Named("loader10")
        BatchLoader<String, String> loader10() {
            return KEYS;
        }

        @Bean
        @Named("loader11")
        BatchLoader<String, String> loader11() {
            return KEYS;
        }

        @Bean
        @Named("loader12")
        BatchLoader<String, String> loader12() {
            return KEYS;
        }

        @Bean
        @Named("loader13")
        BatchLoader<String, String> loader13() {
            return KEYS;
        }

        @Bean
        @Named("loader14")
        BatchLoader<String, String> loader14() {
            return KEYS;
        }

        @Bean
        @Named("loader15")
        BatchLoader<String, String> loader15() {
            return KEYS;
        }

        @Bean
        @Named("loader16")
        BatchLoader<String, String> loader16() {
            return KEYS;
        }

        @Bean
        @Named("loader17")
        BatchLoader<String, String> loader17() {
            return KEYS;
        }

        @Bean
        @Named("loader18")
        BatchLoader<String, String> loader18() {
            return KEYS;
        }

        @Bean
        @Named("loader19")
        BatchLoader<String, String> loader19() {
            return KEYS;
        }

        @Bean
        @Named("loader20")
        BatchLoader<String, String> loader20() {
            return KEYS;
        }

        @Bean
        @Named("loader21")
        BatchLoader<String, String> loader21() {
            return KEYS;
        }

        @Bean
        @Named("loader22")
        BatchLoader<String, String> loader22() {
            return KEYS;
        }

        @Bean
        @Named("loader23")
        BatchLoader<String, String> loader23() {
            return KEYS;
        }

        @Bean
        @Named("loader24")
        BatchLoader<String, String> loader24() {
            return KEYS;
        }

        @Bean
        @Named("loader25")
        BatchLoader<String, String> loader25() {
            return KEYS;
        }

        @Bean
        @Named("loader26")
        BatchLoader<String, String> loader26() {
            return KEYS;
        }

        @Bean
        @Named("loader27")
        BatchLoader<String, String> loader27() {
            return KEYS;
        }

        @Bean
        @Named("loader28")
        BatchLoader<String, String> loader28() {
            return KEYS;
        }

        @Bean
        @Named("loader29")
        BatchLoader<String, String> loader29() {
            return KEYS;
        }

        @Bean
        @Named("loader30")
        BatchLoader<String, String> loader30() {
            return KEYS;
        }

        @Bean
        @Named("loader31")
        BatchLoader<String, String> loader31() {
            return KEYS;
        }

        @Bean
        @Named("loader32")
        BatchLoader<String, String> loader32() {
            return KEYS;
        }

        @Bean
        @Named("loader33")
        BatchLoader<String, String> loader33() {
            return KEYS;
        }

        @Bean
        @Named("loader34")
        BatchLoader<String, String> loader34() {
            return KEYS;
        }

        @Bean
        @Named("loader35")
        BatchLoader<String, String> loader35() {
            return KEYS;
        }

        @Bean
        @Named("loader36")
        BatchLoader<String, String> loader36() {
            return KEYS;
        }

        @Bean
        @Named("loader37")
        BatchLoader<String, String> loader37() {
            return KEYS;
        }

        @Bean
        @Named("loader38")
        BatchLoader<String, String> loader38() {
            return KEYS;
        }

        @Bean
        @Named("loader39")
        BatchLoader<String, String> loader39() {
            return KEYS;
        }

        @Bean
        @Named("loader40")
        BatchLoader<String, String> loader40() {
            return KEYS;
        }

        @Bean
        @Named("loader41")
        BatchLoader<String, String> loader41() {
            return KEYS;
        }

        @Bean
        @Named("loader42")
        BatchLoader<String, String> loader42() {
            return KEYS;
        }

        @Bean
        @Named("loader43")
        BatchLoader<String, String> loader43() {
            return KEYS;
        }

        @Bean
        @Named("loader44")
        BatchLoader<String, String> loader44() {
            return KEYS;
        }

        @Bean
        @Named("loader45")
        BatchLoader<String, String> loader45() {
            return KEYS;
        }

        @Bean
        @Named("loader46")
        BatchLoader<String, String> loader46() {
            return KEYS;
        }

        @Bean
        @Named("loader47")
        BatchLoader<String, String> loader47() {
            return KEYS;
        }

        @Bean
        @Named("loader48")
        BatchLoader<String, String> loader48() {
            return KEYS;
        }

        @Bean
        @Named("loader49")
        BatchLoader<String, String> loader49() {
            return KEYS;
        }
    }

}
