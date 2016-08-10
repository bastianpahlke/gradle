/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.integtests.composite

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.test.fixtures.maven.MavenFileRepository

/**
 * Tests for plugin development scenarios within a composite build.
 */
class CompositeBuildPluginDevelopmentIntegrationTest extends AbstractCompositeBuildIntegrationTest {
    BuildTestFile buildA
    BuildTestFile buildB
    BuildTestFile pluginBuild
    MavenFileRepository mavenRepo

    def setup() {
        mavenRepo = new MavenFileRepository(file("maven-repo"))
        buildA = singleProjectBuild("buildA") {
            buildFile << """
                apply plugin: 'java'
"""
        }

        buildB = singleProjectBuild("buildB") {
            buildFile << """
                apply plugin: 'java'
                version "2.0"
"""
        }

        pluginBuild = pluginProjectBuild("pluginC")
    }

    def "can co-develop plugin and consumer with plugin as included build"() {
        given:
        applyPlugin(buildA)

        buildA.settingsFile << """
            includeBuild('${pluginBuild.toURI()}')
"""

        expect:
        execute(buildA, "tasks")
    }

    @NotYetImplemented // Need to configure buildB (to determine metadata) with pluginC in the context.
    def "can co-develop plugin and consumer with both plugin and consumer as included builds"() {
        given:
        applyPlugin(buildB)

        buildA.buildFile << """
            dependencies {
                compile "org.test:buildB:1.0"
            }
"""
        buildA.settingsFile << """
            includeBuild('${buildB.toURI()}') {
                provides("org.test:buildB:1.0", "buildB::") // By declaring output, don't need to pre-configure
            }
            includeBuild('${pluginBuild.toURI()}')
"""

        expect:
        execute(buildA, "tasks")
    }

    @NotYetImplemented // Need a way to specify exact version to match for substituted dependency
    def "can co-develop plugin and consumer where plugin uses previous version of itself to build"() {
        given:
        // TODO:DAZ Ensure that plugin is published with older version

        pluginBuild.buildFile << """
            buildscript {
                dependencies {
                    classpath 'org.test:pluginC:0.1'
                }
            }
            apply plugin: 'org.test.plugin.pluginC'
"""

        applyPlugin(buildA)

        buildA.settingsFile << """
            includeBuild('${pluginBuild.toURI()}')
"""

        expect:
        execute(buildA, "tasks")
    }

    def applyPlugin(BuildTestFile build) {
        build.buildFile << """
            buildscript {
                dependencies {
                    classpath 'org.test:pluginC:1.0'
                }
            }
            apply plugin: 'org.test.plugin.pluginC'
"""
    }

    def pluginProjectBuild(String name) {
        def className = name.capitalize()
        singleProjectBuild(name) {
            buildFile << """
apply plugin: 'java-gradle-plugin'

gradlePlugin {
    plugins {
        ${name} {
            id = "org.test.plugin.$name"
            implementationClass = "org.test.$className"
        }
    }
}
"""
            file("src/main/java/org/test/${className}.java") << """
package org.test;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class ${className} implements Plugin<Project> {
    public void apply(Project project) {
        System.out.println("Applied ${name}");
    }
}
"""
        }

    }
}