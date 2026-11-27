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
package io.micronaut.graphql.tools.gradle;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

/**
 * The {@code graphqlTools} extension. {@code codegen { }} configures the spec {@code main}; {@code codegen("name") { }} configures
 * another named spec, which gets its own task and its own output directory.
 *
 * <pre>
 * graphqlTools {
 *     codegen {
 *         packageName = "example"
 *     }
 * }
 * </pre>
 */
public abstract class GraphQLToolsExtension {

    private final NamedDomainObjectContainer<CodegenSpec> codegenSpecs;

    /**
     * Creates the extension with the spec {@code main}.
     *
     * @param objects the object factory of the project
     */
    @Inject
    public GraphQLToolsExtension(ObjectFactory objects) {
        this.codegenSpecs = objects.domainObjectContainer(CodegenSpec.class, name -> objects.newInstance(CodegenSpec.class, name));
        this.codegenSpecs.register(CodegenSpec.MAIN);
    }

    /**
     * The version of the generator and of the runtime dependency. Its convention is the version of this plugin.
     *
     * @return the version property
     */
    public abstract Property<String> getVersion();

    /**
     * Whether the plugin adds the library that the generated code uses to {@code implementation}: {@code micronaut-graphql-tools}
     * for the mode {@code SERVER}, {@code micronaut-graphql-tools-client} for {@code CLIENT}. The default is {@code true}.
     *
     * @return the property
     */
    public abstract Property<Boolean> getAddRuntimeDependency();

    /**
     * The directories that hold the schema files, read by the generation tasks and by the compile-time checks. The default is
     * {@code src/main/resources/graphql}.
     *
     * @return the directories
     */
    public abstract ConfigurableFileCollection getSchemaDirs();

    /**
     * All specs by name.
     *
     * @return the container of specs, which has {@code main} from the start
     */
    public NamedDomainObjectContainer<CodegenSpec> getCodegenSpecs() {
        return codegenSpecs;
    }

    /**
     * Configures the spec {@code main}.
     *
     * @param action the configuration
     */
    public void codegen(Action<? super CodegenSpec> action) {
        codegen(CodegenSpec.MAIN, action);
    }

    /**
     * Configures a spec, and creates it when it does not exist yet.
     *
     * @param name   the name of the spec
     * @param action the configuration
     */
    public void codegen(String name, Action<? super CodegenSpec> action) {
        CodegenSpec spec = codegenSpecs.findByName(name);
        if (spec == null) {
            spec = codegenSpecs.create(name);
        }
        action.execute(spec);
    }
}
