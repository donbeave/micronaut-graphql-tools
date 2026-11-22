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
package example.validated

import io.micronaut.core.beans.BeanIntrospector
import io.micronaut.graphql.tools.annotation.GraphQLModels
import io.micronaut.graphql.tools.upload.GraphQLUpload
import spock.lang.Specification

// A test source set lists a class of another module (#528).
@GraphQLModels(classes = [GraphQLUpload])
class OtherModuleModels {
}

class OtherModuleModelsSpec extends Specification {

    def "a class of another module that the test source set lists is introspected"() {
        expect:
        BeanIntrospector.SHARED.findIntrospections { it.beanType == GraphQLUpload }.size() == 1
    }
}
