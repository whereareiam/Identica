plugins {
    id("module-java")
    // The verification tables earlier versions of Identica left, for the schema tests here and the upgrade journeys.
    `java-test-fixtures`
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
