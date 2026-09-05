plugins {
    id("shared")
}

dependencies {
    compileOnly(projects.featureRecognitionApi)
    compileOnly(projects.featureSentinelApi)
    compileOnly(projects.featureVerificationApi)
    compileOnly(projects.providerCredentialApi)

    testImplementation(projects.featureRecognitionApi)
    testImplementation(projects.featureSentinelApi)
    testImplementation(projects.featureVerificationApi)
    testImplementation(projects.providerCredentialApi)
}
