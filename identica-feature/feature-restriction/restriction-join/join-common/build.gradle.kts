plugins {
    id("module-feature")
}

dependencies {
    api(projects.featureRestrictionJoinApi)

    compileOnly(projects.identicaEngine)

    // Its configuration migrations are Strata migrations on Configura documents; Identica provides Strata at runtime.
    compileOnly(libs.strata)
    compileOnly(libs.strata.adapter.configura)

    testImplementation(projects.identicaEngine)

    // The data directories its configuration migrations are tested on.
    testImplementation(testFixtures(projects.identicaCommon))
}

toolkitPublish {
    artifactId.set("restriction-join-common")
}
