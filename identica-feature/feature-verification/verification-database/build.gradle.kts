plugins {
    id("module-java")
}

dependencies {
    implementation(projects.featureVerificationApi)

    compileOnly(libs.jdbi.core)
    compileOnly(libs.jdbi.sqlobject)

    testImplementation(libs.bundles.database)
    testImplementation(libs.dialectica)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.mariadb)
}
