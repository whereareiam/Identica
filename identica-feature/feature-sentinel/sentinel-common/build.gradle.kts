plugins {
    id("module-java")
}

dependencies {
    implementation(projects.featureSentinelApi)

    // Its configuration migrations are Strata migrations on Configura documents; Identica provides Strata at runtime.
    compileOnly(libs.strata)
    compileOnly(libs.strata.adapter.configura)

    // The data directories its configuration migrations are tested on.
    testImplementation(testFixtures(projects.identicaCommon))
}
