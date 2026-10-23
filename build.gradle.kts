/*
 *  Copyright 2023 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

plugins {
    idea
    `teavm-release`
}

group = "net.raymondjxu.carafe"

idea.module.excludeDirs.add(File(rootDir, "build-dir"))

tasks.register("clean") {
    group = "build"
    val buildDir = project.layout.buildDirectory
    doLast {
        buildDir.get().asFile.deleteRecursively()
    }
}
// Publish the compiler/runtime closure consumed by Owlcraft to build/staging-deploy.
// Keep IDE integrations and remote release tasks out of the local development build.
tasks.register("publishForOwlcraft") {
    group = "publishing"
    description = "Publish Carafe compiler and runtime artifacts for the sibling Owlcraft checkout"
    val modules = listOf(
        ":core", ":interop:core", ":extension:apis", ":extension:spi", ":extension:spi-util",
        ":metaprogramming:api", ":metaprogramming:impl", ":jso:core", ":jso:apis", ":jso:impl",
        ":platform", ":classlib", ":tools:core", ":tools:c-incremental", ":tools:cli"
    ) + listOf("asm", "asm-tree", "asm-analysis", "asm-commons", "asm-util", "rhino", "hppc")
        .map { ":relocated:libs:$it" }
    dependsOn(modules.map { "$it:publishAllPublicationsToTeavmRepository" })
}
