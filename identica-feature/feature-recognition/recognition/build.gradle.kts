plugins {
    id("feature")
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
}

toolkitPublish {
    artifactId.set("recognition")
}
