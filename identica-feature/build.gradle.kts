plugins {
    id("module-java")
}

dependencies {
    api(projects.featureRecognitionApi)
    api(projects.featureRestrictionApi)
    api(projects.featureRestrictionJoinApi)
    api(projects.featureSentinelApi)
    api(projects.featureVerificationApi)

    implementation(projects.featureRecognition)
    implementation(projects.featureRestriction)
    implementation(projects.featureRestrictionJoin)
    implementation(projects.featureSentinel)
    implementation(projects.featureVerification)

    compileOnly(projects.identicaCommon)

    // Upgrades the configuration directories earlier versions left with the migrations of the core and every feature.
    testImplementation(projects.identicaCommon)
    testImplementation(projects.featureRecognitionCommon)
    testImplementation(projects.featureRestrictionCommon)
    testImplementation(projects.featureRestrictionJoinCommon)
    testImplementation(projects.featureSentinelCommon)
    testImplementation(projects.featureVerificationCommon)
    testImplementation(testFixtures(projects.identicaCommon))
    testImplementation(libs.commandant)
}
