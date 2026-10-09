plugins {
    id("api")
}

dependencies {
    compileOnly(projects.traitAuthoritativeUsernameApi)
    compileOnly(libs.dialectica)
    compileOnly(libs.jdbi.core)
    compileOnly(libs.jdbi.sqlobject)

    testImplementation(projects.traitAuthoritativeUsernameApi)
    testImplementation(projects.traitAuthoritativeUsernameCommon)
    testImplementation(projects.identicaAdapterDatabase)
    testImplementation(libs.dialectica)
    testImplementation(libs.h2)
    testImplementation(libs.hikaricp)
    testImplementation(libs.jdbi.core)
    testImplementation(libs.jdbi.sqlobject)
    testImplementation(libs.sqlite)
}

toolkitPublish {
    artifactId.set("username-database")
}

group = "me.whereareiam.identica.identity"
