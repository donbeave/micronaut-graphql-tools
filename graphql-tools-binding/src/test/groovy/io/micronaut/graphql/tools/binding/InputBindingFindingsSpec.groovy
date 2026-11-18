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
package io.micronaut.graphql.tools.binding

import graphql.schema.idl.SchemaParser
import io.micronaut.graphql.tools.binding.internal.model.AnnotationNames
import io.micronaut.graphql.tools.binding.internal.model.AnnotationView
import io.micronaut.graphql.tools.binding.internal.model.JClass
import io.micronaut.graphql.tools.binding.internal.model.JEnumConstant
import io.micronaut.graphql.tools.binding.internal.model.JProperty
import io.micronaut.graphql.tools.binding.internal.model.TypeModel
import io.micronaut.graphql.tools.binding.internal.model.TypeRef
import io.micronaut.graphql.tools.binding.internal.rules.ResolverClassFindings
import io.micronaut.graphql.tools.binding.internal.schema.SchemaModel
import spock.lang.Shared
import spock.lang.Specification

import static io.micronaut.graphql.tools.binding.FakeMethods.IGNORED
import static io.micronaut.graphql.tools.binding.FakeMethods.coordinate
import static io.micronaut.graphql.tools.binding.FakeMethods.method
import static io.micronaut.graphql.tools.binding.FakeMethods.primitive
import static io.micronaut.graphql.tools.binding.FakeMethods.ref

/** The input and enum rules of one resolver class, without a compiler and without a bean context. */
class InputBindingFindingsSpec extends Specification {

    static final String SDL = '''\
type Query {
  search(filter: Filter!, other: Filter): String
  count(n: Int, m: Int!, ids: [ID!]): String
  paint(color: Color): String
  tint: Color
  shade(inner: Wrapper): String
  omitted(a: String, b: String!): String
}
input Filter { text: String! }
input Wrapper { filter: Filter  size: Int }
enum Color { RED GREEN }
'''

    static final String ROOT = AnnotationNames.ROOT_RESOLVER
    static final STRING = ref('java.lang.String')

    @Shared
    SchemaModel schema = SchemaModel.load(new SchemaParser().parse(SDL)).get()

    private static AnnotationView view(List<String> names) {
        [has: { String it -> names.contains(it) }, stringValue: { String a, String m -> Optional.empty() },
         classValues: { String a, String m -> [] }] as AnnotationView
    }

    private static JProperty property(String name, TypeRef type = STRING) {
        [name: { name }, type: { type }, annotations: { view([]) }, accessorText: { 'get()' }] as JProperty
    }

    /** an input class: {@code settable} properties can be set, {@code readOnly} ones cannot */
    private static JClass input(String name, List<JProperty> settable, List<JProperty> readOnly = [], boolean introspected = true) {
        [name: { name }, kind: { JClass.Kind.CLASS }, introspected: { introspected }, annotations: { view([]) },
         writableProperties: { settable }, readableProperties: { settable + readOnly },
         isAssignableTo: { String it -> it == name }] as JClass
    }

    private static JClass enumeration(String name, List<String> constants, boolean introspected = true, List<String> ignored = []) {
        [name: { name }, kind: { JClass.Kind.ENUM }, introspected: { introspected }, annotations: { view([]) },
         enumConstants: { constants.collect { new JEnumConstant(it, view(ignored.contains(it) ? [AnnotationNames.IGNORE] : [])) } },
         isAssignableTo: { String it -> it == name }] as JClass
    }

    private static JClass collection(String name) {
        [name: { name }, kind: { JClass.Kind.INTERFACE }, isAssignableTo: { String it -> it == name || it == 'java.lang.Iterable' }] as JClass
    }

    private static TypeModel modelOf(JClass... classes) {
        Map<String, JClass> byName = classes.collectEntries { [(it.name()): it] }
        [resolve: { TypeRef type ->
            byName.get(type.rawName()) ?: [name: { type.rawName() }, kind: { JClass.Kind.CLASS },
                                          isAssignableTo: { String it -> it == type.rawName() }] as JClass
        }] as TypeModel
    }

    private static io.micronaut.graphql.tools.binding.internal.model.JParameter intParameter(String name) {
        [name: { name }, nameIsSynthetic: { false }, type: { primitive('int') }, annotations: { view([]) }] as io.micronaut.graphql.tools.binding.internal.model.JParameter
    }

    private static JClass resolver(List methods) {
        [name: { 'a.R' }, annotations: { view([ROOT]) }, bindableMethods: { methods },
         supertypesAnnotatedWith: { String it -> [] }] as JClass
    }

    private List<String> check(List methods, JClass... classes) {
        ResolverClassFindings.check(schema, modelOf(classes), resolver(methods)).collect { it.code() + ' ' + it.coordinate() }
    }

    void "an input class without an introspection fails at the input type, whichever argument reaches it"() {
        given:
        def methods = [method('search', [:], [FakeModel.parameter('filter', 'a.Filter'), FakeModel.parameter('other', 'a.Filter')],
                STRING, 'a.R#search(a.Filter,a.Filter)')]

        expect:
        check(methods, input('a.Filter', [property('text')], [], false)) == ['class-not-introspected Filter']
    }

    void "a read-only property that no constructor sets is input-field-not-settable at the input field"() {
        given:
        def methods = [method('search', [:], [FakeModel.parameter('filter', 'a.Filter'), FakeModel.parameter('other', 'a.Other')],
                STRING, 'a.R#search(a.Filter,a.Other)')]

        expect:
        check(methods, input('a.Filter', [], [property('text')])) == ['input-field-not-settable Filter.text']
    }

    void "a field that the class has no property for is a failure that only the startup reports, and it masks the strict checks"() {
        given:
        def methods = [method('search', [:], [FakeModel.parameter('filter', 'a.Filter'), FakeModel.parameter('other', 'a.Other')],
                STRING, 'a.R#search(a.Filter,a.Other)'),
                       method('stray', [:], [], STRING, 'a.R#stray()')]

        expect:
        check(methods, input('a.Filter', [property('other')])) == []
    }

    void "a primitive at a nullable argument and at a nullable input field is nullable-primitive, at the argument and at the field"() {
        given:
        def count = method('count', [:], [intParameter('n'), intParameter('m'),
                                          FakeModel.parameter('ids', 'java.util.List')], STRING, 'a.R#count(int,int,java.util.List)')
        def shade = method('shade', [:], [FakeModel.parameter('inner', 'a.Wrapper')], STRING, 'a.R#shade(a.Wrapper)')
        def wrapper = input('a.Wrapper', [property('filter', ref('a.Filter')), property('size', primitive('int'))])

        expect:
        check([count], collection('java.util.List')) == ['nullable-primitive Query.count(n:)']
        check([shade], wrapper, input('a.Filter', [property('text')])) == ['nullable-primitive Wrapper.size']
    }

    void "a list that is not one of the supported collections is unsupported-input-type at the argument"() {
        given:
        def count = method('count', [:], [FakeModel.parameter('n', 'java.lang.Integer'), intParameter('m'),
                                          FakeModel.parameter('ids', 'java.util.ArrayDeque')], STRING,
                'a.R#count(java.lang.Integer,int,java.util.ArrayDeque)')

        expect:
        check([count], collection('java.util.ArrayDeque')) == ['unsupported-input-type Query.count(ids:)']
    }

    void "an ArgumentValue at a nullable position binds what it wraps"() {
        given:
        def wrapper = 'io.micronaut.graphql.tools.ArgumentValue'
        def argument = { String name, TypeRef inner -> [name: { name }, nameIsSynthetic: { false }, type: { ref(wrapper, inner) },
                                                        annotations: { view([]) }] as io.micronaut.graphql.tools.binding.internal.model.JParameter }
        def ok = method('omitted', [:], [argument('a', STRING), FakeModel.parameter('b', 'java.lang.String')], STRING, 'a.R#omitted(av,String)')

        expect:
        check([ok]) == []
    }

    void "an ArgumentValue at a non-null position is unsupported-input-type"() {
        given:
        def wrapper = 'io.micronaut.graphql.tools.ArgumentValue'
        def argument = { String name -> [name: { name }, nameIsSynthetic: { false }, type: { ref(wrapper, STRING) },
                                         annotations: { view([]) }] as io.micronaut.graphql.tools.binding.internal.model.JParameter }
        def atNonNull = method('omitted', [:], [FakeModel.parameter('a', 'java.lang.String'), argument('b')], STRING, 'a.R#omitted(String,av)')

        expect:
        check([atNonNull]) == ['unsupported-input-type Query.omitted(b:)']
    }

    void "the strict checks of the inputs and enums are reported when the class binds, and only then"() {
        given:
        def paint = method('paint', [:], [FakeModel.parameter('color', 'a.Color')], STRING, 'a.R#paint(a.Color)')
        def search = method('search', [:], [FakeModel.parameter('filter', 'a.Filter'), FakeModel.parameter('other', 'a.Filter')],
                STRING, 'a.R#search(a.Filter,a.Filter)')
        def stray = method('stray', [:], [], STRING, 'a.R#stray()')
        def color = enumeration('a.Color', ['RED', 'GREEN', 'BLUE'])
        def filter = input('a.Filter', [property('text'), property('extra')])

        expect:
        check([paint, search], color, filter) == ['unbound-enum-constant a.Color#BLUE', 'unmapped-input-property Filter.extra']
        and: 'a binding failure of the class hides them, as the startup throws it first'
        check([paint, search, method('count', [:], [intParameter('n'), intParameter('m'),
                                                   FakeModel.parameter('ids', 'java.util.List')], STRING, 'a.R#count(int,int,java.util.List)')],
                color, filter, collection('java.util.List')) == ['nullable-primitive Query.count(n:)']
        and: 'an unbound method is a strict check too'
        check([paint, stray], color) == ['unbound-resolver-method a.R#stray()', 'unbound-enum-constant a.Color#BLUE']
    }

    void "a hatch on a constant, and an enum that is not reached, are not findings"() {
        given:
        def paint = method('paint', [:], [FakeModel.parameter('color', 'a.Color')], STRING, 'a.R#paint(a.Color)')

        expect:
        check([paint], enumeration('a.Color', ['RED', 'GREEN', 'BLUE'], true, ['BLUE'])) == []
    }

    void "an enum without an introspection fails at the enum, from an argument and from a return type"() {
        given:
        def paint = method('paint', [:], [FakeModel.parameter('color', 'a.Color')], STRING, 'a.R#paint(a.Color)')
        def tint = method('tint', [:], [], ref('a.Color'), 'a.R#tint()')
        def color = enumeration('a.Color', ['RED', 'GREEN'], false)

        expect:
        check([paint], color) == ['class-not-introspected Color']
        check([tint], color) == ['class-not-introspected Color']
    }

    void "the enum that a list of enums returns ends the walk of the return type"() {
        given:
        def tint = method('tint', [:], [], ref('java.util.List', ref('a.Color')), 'a.R#tint()')

        expect: 'the field is not a list: the startup reports a class mapping, which the compile time leaves out'
        check([tint], enumeration('a.Color', ['RED', 'GREEN', 'BLUE']), collection('java.util.List')) == []
    }

    void "an asynchronous argument is judged by what it wraps"() {
        given:
        def async = { String name, String wrapper -> [name: { name }, nameIsSynthetic: { false }, type: { ref(wrapper, ref('a.Filter')) },
                                                      annotations: { view([]) }] as io.micronaut.graphql.tools.binding.internal.model.JParameter }
        def search = { String wrapper -> method('search', [:], [async('filter', wrapper), FakeModel.parameter('other', 'a.Filter')],
                STRING, 'a.R#search(async,a.Filter)') }
        def notIntrospected = input('a.Filter', [property('text')], [], false)

        expect:
        check([search('java.util.concurrent.CompletionStage')], notIntrospected) == ['class-not-introspected Filter']
        check([search('java.util.concurrent.Future')], notIntrospected) == ['class-not-introspected Filter']
        check([search('java.util.concurrent.Future')], input('a.Filter', [property('text'), property('extra')])) ==
                ['unmapped-input-property Filter.extra']
    }

    void "a wrong class for a built-in scalar return is a class mapping failure of the startup, which hides the strict checks"() {
        given:
        def stray = method('stray', [:], [], STRING, 'a.R#stray()')
        def search = { String returned -> method('search', [:], [FakeModel.parameter('filter', 'a.Filter'), FakeModel.parameter('other', 'a.Filter')],
                ref(returned), 'a.R#search(a.Filter,a.Filter)') }
        def filter = input('a.Filter', [property('text')])

        expect:
        check([search('java.lang.String'), stray], filter) == ['unbound-resolver-method a.R#stray()']
        check([search('java.lang.Integer'), stray], filter) == []
    }

    void "an ArgumentValue around an asynchronous type is not unwrapped again: the startup rejects the class mapping"() {
        given:
        def wrapper = 'io.micronaut.graphql.tools.ArgumentValue'
        def argument = { String name, TypeRef type -> [name: { name }, nameIsSynthetic: { false }, type: { type },
                                                       annotations: { view([]) }] as io.micronaut.graphql.tools.binding.internal.model.JParameter }
        def omitted = method('omitted', [:], [argument('a', ref(wrapper, ref('java.util.concurrent.CompletionStage', ref('a.Filter')))),
                                              FakeModel.parameter('b', 'java.lang.String')], STRING, 'a.R#omitted(av,String)')

        expect:
        check([omitted], input('a.Filter', [property('text')], [], false)) == []
    }

    void "an ArgumentValue around an asynchronous type is not unwrapped again: the startup rejects the class mapping"() {
        given:
        def wrapper = 'io.micronaut.graphql.tools.ArgumentValue'
        def argument = { String name, TypeRef type -> [name: { name }, nameIsSynthetic: { false }, type: { type },
                                                       annotations: { view([]) }] as io.micronaut.graphql.tools.binding.internal.model.JParameter }
        def omitted = method('omitted', [:], [argument('a', ref(wrapper, ref('java.util.concurrent.CompletionStage', ref('a.Filter')))),
                                              FakeModel.parameter('b', 'java.lang.String')], STRING, 'a.R#omitted(av,String)')

        expect:
        check([omitted], input('a.Filter', [property('text')], [], false)) == []
    }

    void "an enum that another use binds to a different class is a mapping conflict: the startup hides the strict checks"() {
        given:
        def paint = method('paint', [:], [FakeModel.parameter('color', 'a.Color')], STRING, 'a.R#paint(a.Color)')
        def tint = method('tint', [:], [], ref('a.Other'), 'a.R#tint()')
        def color = enumeration('a.Color', ['RED', 'GREEN', 'BLUE'])

        expect:
        check([paint], color) == ['unbound-enum-constant a.Color#BLUE']
        check([paint, tint], color, enumeration('a.Other', ['RED', 'GREEN'])) == []
    }

    void "a coordinate on a bean that is not a resolver is coordinate-on-non-resolver"() {
        given:
        JClass bean = [name: { 'a.Bean' }, annotations: { view([]) },
                       bindableMethods: { [method('ping', coordinate('Query.count')), method('plain'),
                                           method('ignored', coordinate('Query.paint') + IGNORED)] },
                       supertypesAnnotatedWith: { String it -> [] }] as JClass

        expect:
        ResolverClassFindings.check(schema, modelOf(), bean).collect { it.code() + ' ' + it.coordinate() } ==
                ['coordinate-on-non-resolver Query.count']
    }

    static final String NESTED = '''\
type Query {
  open(top: Top, other: Other): String
  cycle(outer: Outer): String
  diamond(a: A, b: B): String
}
input Top { first: First  second: Second }
input First { leaf: Leaf }
input Leaf { size: Int }
input Second { text: String }
input Other { text: String }
input Outer { inner: Inner }
input Inner { back: Outer  text: String }
input A { shared: Shared }
input B { shared: Shared }
input Shared { text: String }
'''

    private List<String> checkNested(List methods, JClass... classes) {
        ResolverClassFindings.check(SchemaModel.load(new SchemaParser().parse(NESTED)).get(), modelOf(classes), resolver(methods))
                .collect { it.code() + ' ' + it.coordinate() }
    }

    void "the walk goes depth first in the order of the fields and stops at the first failure: the types after it are not opened"() {
        given:
        def open = method('open', [:], [FakeModel.parameter('top', 'a.Top'), FakeModel.parameter('other', 'a.Other')], STRING,
                'a.R#open(a.Top,a.Other)')
        def top = input('a.Top', [property('first', ref('a.First')), property('second', ref('a.Second'))])
        def first = input('a.First', [property('leaf', ref('a.Leaf'))])
        def leaf = input('a.Leaf', [property('size', primitive('int'))])

        expect: 'Leaf is reached through First before Second, and its failure ends the walk of the argument'
        checkNested([open], top, first, leaf, input('a.Second', [property('text')], [], false), input('a.Other', [property('text')])) ==
                ['nullable-primitive Leaf.size']
        and: 'without it the next type is opened, and its failure is the one that is reported'
        checkNested([open], top, first, input('a.Leaf', [property('size', ref('java.lang.Integer'))]),
                input('a.Second', [property('text')], [], false), input('a.Other', [property('text')])) ==
                ['class-not-introspected Second']
    }

    void "an input type that refers back to an ancestor is bound once, and a strict finding below it is reported once"() {
        given:
        def cycle = method('cycle', [:], [FakeModel.parameter('outer', 'a.Outer')], STRING, 'a.R#cycle(a.Outer)')
        def outer = input('a.Outer', [property('inner', ref('a.Inner'))])
        def inner = input('a.Inner', [property('back', ref('a.Outer')), property('text'), property('extra')])

        expect:
        checkNested([cycle], outer, inner) == ['unmapped-input-property Inner.extra']
    }

    void "a type that two inputs share is bound once: its failure is reported once, at the type"() {
        given:
        def diamond = method('diamond', [:], [FakeModel.parameter('a', 'a.A'), FakeModel.parameter('b', 'a.B')], STRING,
                'a.R#diamond(a.A,a.B)')

        expect:
        checkNested([diamond], input('a.A', [property('shared', ref('a.Shared'))]), input('a.B', [property('shared', ref('a.Shared'))]),
                input('a.Shared', [property('text')], [], false)) == ['class-not-introspected Shared']
    }
}
