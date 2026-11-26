import io.micronaut.graphql.tools.apisnapshot.ApiCheck
import io.micronaut.graphql.tools.apisnapshot.ApiSignatures

// The reviewed snapshot of the public member signatures of a published module (#784): api/<module>.api, generated from the
// main classes with javap. `apiCheck` (part of `check`) fails on any difference; `apiDump` rewrites the snapshot, and the
// change to it is reviewed in the same pull request as the signature change. There is no binary compatibility check here:
// japicmp stays off until 1.0.0 final (see graphql-tools-compatibility).
plugins {
    java
}

val snapshotFile = layout.projectDirectory.file("api/${project.name}.api")

val apiSignatures = tasks.register<ApiSignatures>("apiSignatures") {
    group = "verification"
    description = "Lists the public member signatures of the main classes of ${project.name}."
    classes.from(sourceSets.main.map { it.output.classesDirs })
    listing = layout.buildDirectory.file("api/${project.name}.api")
}

val apiCheck = tasks.register<ApiCheck>("apiCheck") {
    group = "verification"
    description = "Fails when the public member signatures of ${project.name} differ from the reviewed snapshot."
    listing = apiSignatures.flatMap { it.listing }
    snapshot.from(snapshotFile)
    snapshotPath = snapshotFile.asFile.relativeTo(rootProject.projectDir).path
    dumpTask = "${project.path}:apiDump"
}

tasks.register<Copy>("apiDump") {
    group = "verification"
    description = "Rewrites the reviewed snapshot of the public member signatures of ${project.name}."
    from(apiSignatures.flatMap { it.listing })
    into(layout.projectDirectory.dir("api"))
}

tasks.named("check") {
    dependsOn(apiCheck)
}
