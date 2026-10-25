plugins {
    id("module-java")
}

dependencies {
    compileOnly(projects.featureVerificationApi)
    compileOnly(projects.identicaApi)

    testImplementation(projects.featureVerificationApi)
    testImplementation(projects.identicaApi)
}
