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
package io.micronaut.graphql.tools.visitor

import com.sun.source.tree.BlockTree
import com.sun.source.tree.ClassTree
import com.sun.source.tree.CompilationUnitTree
import com.sun.source.tree.IdentifierTree
import com.sun.source.tree.LiteralTree
import com.sun.source.tree.MemberSelectTree
import com.sun.source.tree.MethodInvocationTree
import com.sun.source.tree.NewClassTree
import com.sun.source.tree.Tree
import com.sun.source.tree.VariableTree
import com.sun.source.util.JavacTask
import com.sun.source.util.TreeScanner
import io.micronaut.inject.visitor.TypeElementVisitor
import spock.lang.Shared
import spock.lang.Specification

import javax.lang.model.element.Modifier
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import javax.tools.ToolProvider
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import java.util.stream.Stream

/**
 * What the visitor must not do as a test: the lookups and the files that break Gradle's isolating
 * contract, static state, file writes, reflection and the runtime keys. The sources are parsed with the compiler's tree API,
 * not read as text, so that formatting, a call on a branch no test reaches or a declaration over several lines cannot hide
 * a violation; the last feature proves that each rule fails on a source that breaks it.
 */
class VisitorArchitectureSpec extends Specification {

    static final Path SOURCES = Path.of(System.getProperty('visitor.sources'))

    // calls by method name, whatever the receiver: the lookups and the writes of Micronaut's visitor API, and File writes
    static final Set<String> FORBIDDEN_METHODS = [
            'getClassesOutputPath', 'getClasspathResources', 'getClassElement', 'getClassElements',
            'getRequiredClassElement', 'visitMetaInfFile', 'visitGeneratedFile', 'visitGeneratedSourceFile',
            'visitServiceDescriptor', 'addGeneratedResource', 'createNewFile', 'mkdir', 'mkdirs', 'renameTo', 'deleteOnExit',
    ] as Set

    // calls by receiver and method name: ambient state of the JVM, class loading by name, and writes through java.nio
    static final Map<String, Set<String>> FORBIDDEN_QUALIFIED = [
            System: ['getProperty', 'getProperties', 'setProperty', 'clearProperty', 'getenv', 'exit'] as Set,
            Class : ['forName'] as Set,
            Files : ['write', 'writeString', 'newOutputStream', 'newBufferedWriter', 'createFile', 'createDirectory',
                     'createDirectories', 'createTempFile', 'createTempDirectory', 'createLink', 'createSymbolicLink',
                     'copy', 'move', 'delete', 'deleteIfExists', 'setAttribute', 'setLastModifiedTime'] as Set,
    ]

    static final Set<String> FORBIDDEN_TYPES = ['FileWriter', 'FileOutputStream', 'RandomAccessFile'] as Set

    // the initialisers that cannot be mutated: a static final field is allowed only when its type or its initialiser is one of these
    static final Set<String> IMMUTABLE_TYPES = ['String', 'Pattern', 'boolean', 'byte', 'char', 'short', 'int', 'long',
                                                'float', 'double'] as Set
    static final Set<String> IMMUTABLE_FACTORIES = ['Set.of', 'List.of', 'Map.of', 'Map.ofEntries', 'Set.copyOf', 'List.copyOf',
                                                    'Map.copyOf', 'Pattern.compile', 'Collections.emptySet',
                                                    'Collections.emptyList', 'Collections.emptyMap'] as Set

    @Shared
    GraphQLToolsVisitor visitor = new GraphQLToolsVisitor()

    def "the sources are found"() {
        expect:
        files().size() >= 5
    }

    def "no class of the visitor breaks the isolating contract, writes a file, keeps static state or uses reflection"() {
        expect:
        violations(parse(files().collect { new FileSource(it) })) == []
    }

    def "the isolating visitor claims exactly the six annotations, the six options and the retired keys"() {
        expect:
        visitor.visitorKind == TypeElementVisitor.VisitorKind.ISOLATING
        visitor.supportedAnnotationNames == [
                'io.micronaut.graphql.tools.annotation.GraphQLRootResolver',
                'io.micronaut.graphql.tools.annotation.GraphQLTypeResolver',
                'io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate',
                'io.micronaut.graphql.tools.annotation.GraphQLModels',
                'io.micronaut.graphql.tools.annotation.GraphQLType', 'io.micronaut.core.annotation.Introspected'] as Set
        visitor.supportedOptions == ['micronaut.graphql.tools.processor.schema.dirs', 'micronaut.graphql.tools.processor.module.dir',
                                     'micronaut.graphql.tools.processor.checks',
                                     'micronaut.graphql.tools.processor.resolver.accessor.prefixes',
                                     'micronaut.graphql.tools.processor.loose.nullability',
                                     'micronaut.graphql.tools.processor.type.class.convention',
                                     'micronaut.graphql.tools.processor.resolverAccessorPrefixes',
                                     'micronaut.graphql.tools.processor.looseNullability',
                                     'micronaut.graphql.tools.processor.typeClassConvention',
                                     'micronaut.graphql.tools.sdlDir'] as Set
    }

    def "each rule fails on a source that breaks it, however the source is formatted"() {
        when:
        def found = violations(parse([new StringSource('Bad', source)]))

        then:
        found.size() == 1
        found[0].contains(expected)

        where:
        source                                                                                   | expected
        'class Bad { void m(Object c) { c\n.getClasspathResources(); } }'                         | 'getClasspathResources'
        'class Bad { void m(Object c) { c.getClassElement ( "x" ); } }'                          | 'getClassElement'
        'class Bad { void m(Object c) { c.visitMetaInfFile("x"); } }'                            | 'visitMetaInfFile'
        'class Bad { String m() { return System\n.getProperty("x"); } }'                         | 'System.getProperty'
        'class Bad { void m() { java.lang.System.exit(1); } }'                                   | 'System.exit'
        'class Bad { Object m() throws Exception { return Class.forName("x"); } }'               | 'Class.forName'
        'class Bad { void m(java.nio.file.Path p) throws Exception { java.nio.file.Files\n.writeString(p, "x"); } }' | 'Files.writeString'
        'class Bad { void m() throws Exception { new java.io.FileWriter("x"); } }'               | 'FileWriter'
        'class Bad { void m(java.io.File f) throws Exception { f.createNewFile(); } }'           | 'createNewFile'
        'class Bad { private static\n int count\n = 0; }'                                        | 'static field count'
        'class Bad { static final java.util.Map<String, String> CACHE\n = new java.util.HashMap<>(); }' | 'static field CACHE'
        'class Bad { static final java.util.List<String> NAMES = new java.util.ArrayList<>(); }' | 'static field NAMES'
        'class Bad { static { System.out.println(); } }'                                         | 'static initializer'
        'import java.lang.reflect.Method;\nclass Bad { Method m; }'                               | 'java.lang.reflect'
        'class Bad { Object m(Object o) throws Exception { return o.getClass().getMethods(); } }' | 'getMethods'
        'class Bad { Object v = io.micronaut.inject.visitor.TypeElementVisitor.VisitorKind.AGGREGATING; }' | 'AGGREGATING'
    }

    def "what the visitor may do is not flagged"() {
        expect:
        violations(parse([new StringSource('Good', '''
import java.util.*;
import java.util.regex.Pattern;
class Good {
    private static final String TAG = "x";
    private static final Set<String> NAMES = Set.of("a");
    private static final Pattern NAME = Pattern.compile("a");
    private static final class State { boolean failed; int count; }
    private final Map<String, String> perInstance = new HashMap<>();
    static String helper(String s) { return s; }
}''')])) == []
    }

    private static List<Path> files() {
        try (Stream<Path> stream = Files.walk(SOURCES)) {
            return stream.filter { it.toString().endsWith('.java') }.collect(Collectors.toList())
        }
    }

    private static List<CompilationUnitTree> parse(List<JavaFileObject> sources) {
        def compiler = ToolProvider.systemJavaCompiler
        def fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)
        try {
            JavacTask task = (JavacTask) compiler.getTask(null, fileManager, null, ['-proc:none'], null, sources)
            return task.parse().toList()
        } finally {
            fileManager.close()
        }
    }

    private static List<String> violations(List<CompilationUnitTree> units) {
        List<String> found = []
        units.each { unit ->
            String file = unit.sourceFile.name
            new TreeScanner<Void, Void>() {

                @Override
                Void visitMemberSelect(MemberSelectTree node, Void p) {
                    if (node.expression.toString().startsWith('java.lang.reflect')) {
                        found << "${file}: uses java.lang.reflect".toString()
                    }
                    if (node.identifier.toString() == 'AGGREGATING') {
                        found << "${file}: uses AGGREGATING".toString()
                    }
                    return super.visitMemberSelect(node, p)
                }

                @Override
                Void visitIdentifier(IdentifierTree node, Void p) {
                    if (node.name.toString() == 'AGGREGATING') {
                        found << "${file}: uses AGGREGATING".toString()
                    }
                    return super.visitIdentifier(node, p)
                }

                @Override
                Void visitMethodInvocation(MethodInvocationTree node, Void p) {
                    def select = node.methodSelect
                    String name = select instanceof MemberSelectTree ? select.identifier.toString() : select.toString()
                    String receiver = select instanceof MemberSelectTree ? select.expression.toString() : ''
                    String simpleReceiver = receiver.substring(receiver.lastIndexOf('.') + 1)
                    if (FORBIDDEN_METHODS.contains(name) && !(name == 'getClassesOutputPath' && file.endsWith('ModuleOwnership.java'))) {
                        found << "${file}: calls ${name}".toString()
                    }
                    if (FORBIDDEN_QUALIFIED.getOrDefault(simpleReceiver, [] as Set).contains(name)) {
                        found << "${file}: calls ${simpleReceiver}.${name}".toString()
                    }
                    if (name == 'getMethods' || name == 'getDeclaredMethods' || name == 'getDeclaredFields') {
                        found << "${file}: calls ${name} (reflection)".toString()
                    }
                    return super.visitMethodInvocation(node, p)
                }

                @Override
                Void visitNewClass(NewClassTree node, Void p) {
                    String type = node.identifier.toString()
                    if (FORBIDDEN_TYPES.contains(type.substring(type.lastIndexOf('.') + 1))) {
                        found << "${file}: creates a ${type}".toString()
                    }
                    return super.visitNewClass(node, p)
                }

                @Override
                Void visitClass(ClassTree node, Void p) {
                    boolean isInterface = node.kind == Tree.Kind.INTERFACE || node.kind == Tree.Kind.ANNOTATION_TYPE
                    for (Tree member : node.members) {
                        if (member instanceof VariableTree) {
                            VariableTree field = (VariableTree) member
                            boolean isStatic = isInterface || field.modifiers.flags.contains(Modifier.STATIC)
                            boolean isFinal = isInterface || field.modifiers.flags.contains(Modifier.FINAL)
                            if (isStatic && !isFinal) {
                                found << "${file}: static field ${field.name} is not final".toString()
                            } else if (isStatic && !VisitorArchitectureSpec.immutable(field)) {
                                found << "${file}: static field ${field.name} holds a mutable value".toString()
                            }
                        } else if (member instanceof BlockTree && ((BlockTree) member).isStatic()) {
                            found << "${file}: has a static initializer".toString()
                        }
                    }
                    return super.visitClass(node, p)
                }
            }.scan(unit, null)
        }
        return found
    }

    // a static final field is allowed only when its type or its initialiser cannot be mutated
    private static boolean immutable(VariableTree field) {
        String type = field.type.toString()
        if (IMMUTABLE_TYPES.contains(type.substring(type.lastIndexOf('.') + 1))) {
            return true
        }
        def init = field.initializer
        if (init instanceof LiteralTree) {
            return true
        }
        if (init instanceof MethodInvocationTree) {
            String call = init.methodSelect.toString()
            return IMMUTABLE_FACTORIES.any { call == it || call.endsWith('.' + it) } || call.startsWith('Collections.unmodifiable')
        }
        return false
    }

    private static class FileSource extends SimpleJavaFileObject {

        private final Path path

        FileSource(Path path) {
            super(path.toUri(), JavaFileObject.Kind.SOURCE)
            this.path = path
        }

        @Override
        CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return Files.readString(path)
        }
    }

    private static class StringSource extends SimpleJavaFileObject {

        private final String content

        StringSource(String name, String content) {
            super(URI.create('string:///' + name + '.java'), JavaFileObject.Kind.SOURCE)
            this.content = content
        }

        @Override
        CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return content
        }
    }
}
