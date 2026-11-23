plugins {
    groovy
}

dependencies {
    implementation("org.apache.groovy:groovy:${providers.gradleProperty("groovyVersion").get()}")
    // The Groovy compiler loads a global AST transformation from its compile classpath.
    compileOnly(project(":probe"))
}
