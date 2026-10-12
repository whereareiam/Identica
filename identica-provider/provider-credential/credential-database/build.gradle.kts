plugins {
    id("module-java")
}

dependencies {
    compileOnly(projects.providerCredentialApi)
    compileOnly(projects.identicaAdapterDatabaseApi)
    compileOnly(libs.jdbi.core)
    compileOnly(libs.jdbi.sqlobject)
    compileOnly(libs.dialectica.adapter.jdbi)

    testImplementation(projects.providerCredentialApi)
    testImplementation(projects.identicaAdapterDatabaseCommon)
    testImplementation(testFixtures(projects.identicaAdapterDatabaseCommon))
}
