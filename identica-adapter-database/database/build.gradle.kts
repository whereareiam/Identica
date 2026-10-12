plugins {
    id("module-java")
}

// Wires the database family into Identica's injector; the implementation is in database-common.
dependencies {
    implementation(projects.identicaAdapterDatabaseCommon)

    compileOnly(projects.identicaAdapterDatabaseApi)
    compileOnly(libs.jdbi.core)
    compileOnly(libs.jdbi.sqlobject)
    compileOnly(libs.dialectica.adapter.jdbi)

    testImplementation(projects.identicaAdapterDatabaseApi)
    testImplementation(libs.dialectica.adapter.jdbi)
    testImplementation(libs.jdbi.core)
}
