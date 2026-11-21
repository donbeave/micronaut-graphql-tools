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
package example.validated;

import example.validated.ValidatedModels.Direct;
import example.validated.ValidatedModels.Listed;
import example.validated.ValidatedModels.Plain;
import example.validated.ValidatedModels.Twice;
import example.validated.generated.Generated;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The classes that @GraphQLModels lists keep their introspections when Micronaut Validation's processor runs in the same
// compilation (#528). Runs on the JVM and as a native binary (nativeTest).
class ValidatedModelsTest {

    private static long introspections(Class<?> type) {
        return BeanIntrospector.SHARED.findIntrospections(reference -> reference.getBeanType() == type).size();
    }

    @Test
    void aListedClassIsIntrospected() {
        assertTrue(BeanIntrospection.getIntrospection(Plain.class).getProperty("name").isPresent());
        assertTrue(BeanIntrospection.getIntrospection(Listed.class).getProperty("label").isPresent());
    }

    @Test
    void theClassesOfAListedPackageAreIntrospected() {
        assertTrue(BeanIntrospection.getIntrospection(Generated.class).getProperty("id").isPresent());
    }

    @Test
    void aDirectIntrospectedKeepsWorking() {
        assertEquals(1, introspections(Direct.class));
    }

    @Test
    void aClassListedTwiceIsIntrospectedOnce() {
        assertEquals(1, introspections(Twice.class));
    }

}
