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
package io.micronaut.graphql.tools.internal.model;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanMethod;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.graphql.tools.internal.manifest.BindingManifestRecorder;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.micronaut.graphql.tools.internal.MicronautUtils.toMap;

/**
 * The property or the method of a model class that can bind a field.
 *
 * @param beanIntrospection the introspection of the model class
 * @param beanProperty      the property, or {@code null} when a method binds the field
 * @param beanMethod        the method, or {@code null} when a property binds the field
 */
@Internal
public record ModelMember(BeanIntrospection<Object> beanIntrospection,
                   @Nullable BeanProperty<Object, Object> beanProperty,
                   @Nullable BeanMethod<Object, ?> beanMethod) {

    public Map<Class<?>, List<String>> members() {
        return beanProperty != null
                ? toMap(Collections.emptyList(), beanProperty)
                : toMap(List.of(beanMethod), List.of());
    }

    public String manifestMember() {
        return beanProperty != null
                ? BindingManifestRecorder.propertyMember(beanProperty)
                : BindingManifestRecorder.introspectedMethodMember(beanMethod,
                beanMethod.getDeclaringBean().getBeanType());
    }

}
