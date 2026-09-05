plugins {
    id("shared")
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
}
