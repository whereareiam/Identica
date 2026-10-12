plugins {
    id("module-java")
    // The credential tables earlier versions of Identica left, for the schema tests here and the upgrade journeys.
    `java-test-fixtures`
}

dependencies {
    compileOnly(projects.providerCredentialApi)
    compileOnly(projects.identicaAdapterDatabaseApi)
    compileOnly(libs.strata.adapter.database.dialectica)
    compileOnly(libs.jdbi.core)
    compileOnly(libs.jdbi.sqlobject)
    compileOnly(libs.dialectica.adapter.jdbi)

    testImplementation(projects.providerCredentialApi)
    testImplementation(projects.identicaAdapterDatabaseCommon)
    testImplementation(testFixtures(projects.identicaAdapterDatabaseCommon))
}
