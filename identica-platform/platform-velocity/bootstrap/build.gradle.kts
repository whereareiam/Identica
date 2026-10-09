plugins {
    id("platform")
    alias(libs.plugins.attache)
}

platform {
    name.set("VELOCITY")
    descriptors.add("velocity-plugin.json")
}

dependencies {
    testImplementation(libs.jdbi.core)
    testImplementation(libs.jdbi.sqlobject)
    testImplementation(libs.dialectica)
    testImplementation(libs.commandant)
    testImplementation(libs.velocity)
    testImplementation(libs.cloud.velocity)

    implementation(projects.platformVelocityApi)
    implementation(libs.bundles.bStats.velocity)
    implementation(libs.attache.velocity)

    compileOnly(libs.velocity)
    annotationProcessor(libs.velocity)

    attache(libs.cloud.velocity)
}
