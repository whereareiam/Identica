plugins {
    id("packaging-platform-jar")
    alias(libs.plugins.attache)
}

platform {
    name.set("VELOCITY")
    descriptors.add("velocity-plugin.json")
}

dependencies {
    testImplementation(libs.jdbi.core)
    testImplementation(libs.jdbi.sqlobject)
    testImplementation(libs.dialectica.bundle)
    testImplementation(libs.dialectica.adapter.jdbi)
    testImplementation(projects.identicaAdapterDatabaseApi)
    testImplementation(libs.commandant)
    testImplementation(libs.velocity)
    testImplementation(libs.cloud.velocity)

    implementation(projects.platformVelocityApi)
    implementation(libs.statistica.platform.velocity) {
        capabilities { requireCapability("me.whereareiam:statistica-platform-velocity-bstats") }
    }
    implementation(libs.statistica.platform.velocity) {
        capabilities { requireCapability("me.whereareiam:statistica-platform-velocity-faststats") }
    }
    implementation(libs.attache.velocity)

    compileOnly(libs.velocity)
    annotationProcessor(libs.velocity)

    attache(libs.cloud.velocity)

    testRuntimeOnly(libs.strata)
    testRuntimeOnly(libs.strata.adapter.configura)
    testRuntimeOnly(libs.strata.adapter.database)
    testRuntimeOnly(libs.jedis)
    testRuntimeOnly(libs.hikaricp)
}
