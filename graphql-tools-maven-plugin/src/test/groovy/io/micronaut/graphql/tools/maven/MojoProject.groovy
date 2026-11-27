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
package io.micronaut.graphql.tools.maven

import org.apache.maven.plugin.testing.MojoRule
import org.apache.maven.project.MavenProject

import java.nio.file.Files
import java.nio.file.Path

/**
 * A throwaway Maven project whose pom declares the plugin with a configuration, and the mojo that the harness binds from it: the
 * descriptor on the class path (the hand-written plugin.xml), the configuration of the pom, then the defaults of the descriptor.
 */
final class MojoProject {

    final Path dir
    final MavenProject project
    final GenerateMojo mojo

    private MojoProject(Path dir, MavenProject project, GenerateMojo mojo) {
        this.dir = dir
        this.project = project
        this.mojo = mojo
    }

    static MojoProject create(MojoRule rule, Path dir, String configuration, Map<String, String> files = [:]) {
        files.each { name, content ->
            Path file = dir.resolve(name)
            Files.createDirectories(file.parent)
            Files.writeString(file, content)
        }
        Files.writeString(dir.resolve('pom.xml'), """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>example</groupId>
  <artifactId>app</artifactId>
  <version>1.0</version>
  <build>
    <plugins>
      <plugin>
        <groupId>io.micronaut.graphql.tools</groupId>
        <artifactId>micronaut-graphql-tools-maven-plugin</artifactId>
        <version>${System.getProperty('plugin.version')}</version>
        <configuration>
          ${configuration}
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
""")
        MavenProject project = rule.readMavenProject(dir.toFile())
        GenerateMojo mojo = (GenerateMojo) rule.lookupConfiguredMojo(project, 'generate')
        return new MojoProject(dir, project, mojo)
    }
}
