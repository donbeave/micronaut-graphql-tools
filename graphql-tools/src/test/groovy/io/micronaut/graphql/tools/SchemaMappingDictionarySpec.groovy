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
package io.micronaut.graphql.tools

import io.micronaut.graphql.tools.mapping.SchemaMappingDictionary
import io.micronaut.core.annotation.Introspected
import spock.lang.Specification

class SchemaMappingDictionarySpec extends Specification {

    void "register an interface"() {
        when:
        def dictionary = new SchemaMappingDictionary().registerType("User", User)

        then:
        dictionary.types == [User: User]
    }

    void "attempt to register an enum, an annotation or an array"() {
        when:
        new SchemaMappingDictionary().registerType("User", type)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "GraphQL type `User` can only be mapped to a class or an interface, found ${type.name}."

        where:
        type << [Status, Marker, String[]]
    }

    void "attempt to register the duplicate"() {
        when:
        new SchemaMappingDictionary()
                .registerType("PaymentMethod", PaymentMethod)
                .registerType("PaymentMethod", PaymentMethod)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "GraphQL type `PaymentMethod` is already mapped to ${PaymentMethod.name}."
    }

    void "attempt to register one GraphQL type with two classes"() {
        when:
        new SchemaMappingDictionary()
                .registerType("PaymentMethod", PaymentMethod)
                .registerType("PaymentMethod", User)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Unable to map GraphQL type `PaymentMethod` to ${User.name}, as it is already mapped to ${PaymentMethod.name}."
    }

    void "attempt to register two GraphQL types with one class"() {
        when:
        new SchemaMappingDictionary()
                .registerType("PaymentMethod", PaymentMethod)
                .registerType("User", PaymentMethod)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Unable to map GraphQL type `User` to ${PaymentMethod.name}, as GraphQL type `PaymentMethod` is " +
                "already mapped to it. One class can only be mapped to one GraphQL type."
    }

    static interface User {
        String getUsername()
    }

    static class PaymentMethod {
        String cardholder
        String cardNumber
    }

    @Introspected
    static enum Status {
        ACTIVE
    }

    static @interface Marker {
    }

}
