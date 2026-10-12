plugins {
    id("module-java")
}

dependencies {
    implementation(projects.featureVerificationApi)

    compileOnly(projects.identicaAdapterDatabaseApi)
    compileOnly(libs.jdbi.core)
    compileOnly(libs.jdbi.sqlobject)
    compileOnly(libs.dialectica.adapter.jdbi)

    testImplementation(libs.jdbi.core)
    testImplementation(libs.jdbi.sqlobject)
    testImplementation(projects.identicaAdapterDatabaseCommon)
    testImplementation(testFixtures(projects.identicaAdapterDatabaseCommon))
}
