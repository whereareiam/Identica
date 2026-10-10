plugins {
    id("module-java")
}

dependencies {
    compileOnly(projects.featureVerificationApi)
    compileOnly(libs.bStats)
}
