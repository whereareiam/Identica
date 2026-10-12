plugins {
    id("module-java")
}

dependencies {
    implementation(projects.featureVerificationApi)

    compileOnly(projects.identicaAdapterCommand)
    compileOnly(libs.cloud.core)

    // Its configuration migrations are Strata migrations on Configura documents; Identica provides Strata at runtime.
    compileOnly(libs.strata)
    compileOnly(libs.strata.adapter.configura)

    testImplementation(projects.identicaAdapterCommand)
    testImplementation(libs.cloud.core)

    // The data directories its configuration migrations are tested on.
    testImplementation(testFixtures(projects.identicaCommon))
}
