plugins {
    `java-library`
}

// a module of resources: the JVM level that a consumer sees is the one of the modules that depend on it, not the JDK of the build
java {
    sourceCompatibility = JavaVersion.toVersion("21")
    targetCompatibility = JavaVersion.toVersion("21")
}
