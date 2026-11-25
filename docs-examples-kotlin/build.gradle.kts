// The user guide examples, apart from the behavioural tests of the test-suite-* modules: the guide includes their sources with `snippet::`.
plugins {
    id("io.micronaut.build.internal.graphql-tools-license")
    id("io.micronaut.library")
    id("org.jetbrains.kotlin.jvm")
    id("com.google.devtools.ksp")
}

repositories {
    mavenCentral()
}

micronaut {
    testRuntime("junit5")
    // The platform version: micronaut-build reads `micronaut` as the core BOM version, which has no platform of the same number.
    version.set(libs.versions.micronaut.platform)
    processing {
        incremental(true)
        annotations("example.*")
    }
}

dependencies {
    implementation(projects.micronautGraphqlTools)
    implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    ksp("io.micronaut:micronaut-inject-kotlin")
    kspTest("io.micronaut:micronaut-inject-kotlin")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    sourceCompatibility = JavaVersion.toVersion("21")
    targetCompatibility = JavaVersion.toVersion("21")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        javaParameters.set(true)
    }
}
