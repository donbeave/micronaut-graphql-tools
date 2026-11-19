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
package io.micronaut.graphql.tools.schema;

import graphql.TypeResolutionEnvironment;
import graphql.execution.TypeResolutionParameters;
import graphql.schema.GraphQLSchema;
import graphql.schema.TypeResolver;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.List;

final class DiscardableClassLoaderFixture {

    public static class Album {
    }

    public static class LiveAlbum extends Album {
    }

    private static final class ChildFirstLoader extends ClassLoader {

        ChildFirstLoader() {
            super(DiscardableClassLoaderFixture.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.equals(LiveAlbum.class.getName())) {
                return super.loadClass(name, resolve);
            }
            try (InputStream in = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                byte[] bytes = in.readAllBytes();
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }
    }

    private DiscardableClassLoaderFixture() {
    }

    static List<WeakReference<?>> resolveFromDiscardableLoader(TypeResolver resolver, GraphQLSchema schema,
                                                                           String expectedType)
            throws ReflectiveOperationException {
        ChildFirstLoader loader = new ChildFirstLoader();
        Class<?> loaded = loader.loadClass(LiveAlbum.class.getName());
        if (loaded == LiveAlbum.class) {
            throw new IllegalStateException("the class was not loaded by its own class loader");
        }
        Object instance = loaded.getDeclaredConstructor().newInstance();
        TypeResolutionEnvironment env = TypeResolutionParameters.newParameters().value(instance).schema(schema).build();
        String resolved = resolver.getType(env).getName();
        if (!expectedType.equals(resolved)) {
            throw new IllegalStateException("resolved " + resolved + ", expected " + expectedType);
        }
        return List.of(new WeakReference<>(loaded), new WeakReference<>(loader));
    }
}
