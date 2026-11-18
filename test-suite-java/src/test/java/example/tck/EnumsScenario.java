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
package example.tck;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.annotation.GraphQLInput;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The fixtures of the TCK scenario {@code enums}.
 */
public final class EnumsScenario {

    private EnumsScenario() {
    }

    @Introspected
    public enum Level {
        LOW, MEDIUM, HIGH
    }

    @GraphQLInput
    public static class Window {
        private Level from;
        private Level to;
        private List<Level> only;

        public Level getFrom() {
            return from;
        }

        public void setFrom(Level from) {
            this.from = from;
        }

        public Level getTo() {
            return to;
        }

        public void setTo(Level to) {
            this.to = to;
        }

        public List<Level> getOnly() {
            return only;
        }

        public void setOnly(List<Level> only) {
            this.only = only;
        }
    }

    @GraphQLRootResolver
    @Requires(property = "tck.scenario", value = "enums")
    public static class Queries {

        public int rank(Level level) {
            return level.ordinal();
        }

        public Level next(Level level) {
            return Level.values()[(level.ordinal() + 1) % Level.values().length];
        }

        public List<Level> pick(List<Level> levels) {
            return levels.stream().sorted(Comparator.naturalOrder()).collect(Collectors.toList());
        }

        @Nullable
        public Level maybe(@Nullable Level level) {
            return level;
        }

        public String window(Window window) {
            return "from=" + window.getFrom() + " to=" + window.getTo() + " only=" + window.getOnly();
        }
    }

}
