plugins {
    id("shared")
}

dependencies {
    implementation(projects.featureVerificationApi)

    compileOnly(libs.jdbi.core)
    compileOnly(libs.jdbi.sqlobject)

    testImplementation(libs.jdbi.core)
    testImplementation(libs.jdbi.sqlobject)
}
