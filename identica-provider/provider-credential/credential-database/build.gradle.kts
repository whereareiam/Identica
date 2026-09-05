plugins {
    id("shared")
}

dependencies {
    compileOnly(projects.providerCredentialApi)
    compileOnly(libs.dialectica)
    compileOnly(libs.jdbi.core)
    compileOnly(libs.jdbi.sqlobject)

    testImplementation(projects.identicaAdapterDatabase)
    testImplementation(projects.providerCredentialApi)
    testImplementation(libs.dialectica)
    testImplementation(libs.h2)
    testImplementation(libs.hikaricp)
    testImplementation(libs.jdbi.core)
    testImplementation(libs.jdbi.sqlobject)
    testImplementation(libs.sqlite)
}
