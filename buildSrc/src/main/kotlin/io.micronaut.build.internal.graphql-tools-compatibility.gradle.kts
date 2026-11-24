// There is no japicmp baseline before 1.0.0 final, so the japicmp check stays off. It is switched on here, in this one
// place, after 1.0.0 final together with config/accepted-api-changes.json for the accepted breaks (#217).
extensions.configure<io.micronaut.build.MicronautBuildExtension> {
    (this as ExtensionAware).extensions.configure<io.micronaut.build.compat.BinaryCompatibibilityExtension>("binaryCompatibility") {
        enabled.set(false)
    }
}
