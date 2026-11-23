plugins {
    `java-library`
}

val graphqlJavaVersion = providers.gradleProperty("graphqlJavaVersion").get()

dependencies {
    api("com.graphql-java:graphql-java:$graphqlJavaVersion")
    compileOnly("org.apache.groovy:groovy:${providers.gradleProperty("groovyVersion").get()}")
    compileOnly("com.google.devtools.ksp:symbol-processing-api:${providers.gradleProperty("kspVersion").get()}")
}
