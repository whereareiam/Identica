plugins {
    id("shared")
}

dependencies {
    implementation(projects.featureVerificationApi)

    compileOnly(projects.identicaAdapterCommand)
    compileOnly(libs.cloud.core)

    testImplementation(projects.identicaAdapterCommand)
    testImplementation(libs.cloud.core)
}
