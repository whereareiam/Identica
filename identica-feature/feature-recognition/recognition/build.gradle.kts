plugins {
    id("module-feature")
}

dependencies {
    api(projects.featureRecognitionApi)

    implementation(projects.featureRecognitionCommon)

    testImplementation(projects.featureRestriction)
    testImplementation(projects.featureRestrictionCommon)
    testImplementation(projects.featureRestrictionJoin)
    testImplementation(projects.featureRestrictionJoinCommon)
    testImplementation(projects.identicaEngine)
    testImplementation(libs.cloud.core)
    testImplementation(libs.commandant)
    // The features' configuration migrations, which their modules register, are Strata migrations.
    testImplementation(libs.strata.adapter.configura)
}

toolkitPublish {
    artifactId.set("recognition")
}
