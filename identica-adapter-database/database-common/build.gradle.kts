import me.whereareiam.attache.plugin.gradle.extension.AttacheExtension

plugins {
    id("module-java")
    `java-test-fixtures`
    alias(libs.plugins.attache)
}

dependencies {
    compileOnly(projects.identicaAdapterDatabaseApi)

    attache(libs.dialectica.bundle)
    attache(libs.dialectica.adapter.jdbi)
    attache(libs.strata)
    attache(libs.strata.adapter.database)
    attache(libs.strata.adapter.database.jdbi)
    attache(libs.strata.adapter.database.dialectica)
    attache(libs.jdbi.core)
    attache(libs.jdbi.sqlobject)
    attache(libs.hikaricp)

    compileOnly(libs.strata)
    compileOnly(libs.strata.adapter.database)
    compileOnly(libs.strata.adapter.database.jdbi)
    compileOnly(libs.strata.adapter.database.dialectica)

    // The databases that schema tests run against, shared with the modules that declare a schema.
    testFixturesApi(project(":identica-api"))
    testFixturesApi(projects.identicaAdapterDatabaseApi)
    testFixturesApi(libs.bundles.database)
    testFixturesApi(libs.dialectica.bundle)
    testFixturesApi(libs.dialectica.adapter.jdbi)
    testFixturesApi(libs.strata)
    testFixturesApi(libs.strata.adapter.database)
    testFixturesApi(libs.strata.adapter.database.jdbi)
    testFixturesApi(libs.strata.adapter.database.dialectica)
    testFixturesApi(libs.testcontainers.junit)
    testFixturesApi(libs.testcontainers.postgresql)
    testFixturesApi(libs.testcontainers.mariadb)
    testFixturesApi(libs.testcontainers.mysql)
    // Testcontainers waits for a MySQL container through MySQL's own driver; Identica uses MariaDB's.
    testFixturesRuntimeOnly(libs.mysql)
    testFixturesApi(libs.junit.jupiter)
    testFixturesImplementation(libs.attache.common)
    testFixturesImplementation(libs.guice)
    testFixturesImplementation(libs.mockito.core)
    testFixturesImplementation(libs.annotations)
}

extensions.configure<AttacheExtension>("attache") {
    library(libs.dialectica.adapter.jdbi) {
        relocate("org{}jdbi", "me.whereareiam.identica.library.jdbi")
        relocate("io{}leangen{}geantyref", "me.whereareiam.identica.library.geantyref")
        // Dialectica comes from the bundle, in the version Identica declares.
        excludeTransitive("me.whereareiam", "dialectica-api")
        excludeTransitive("me.whereareiam", "dialectica-common")
    }

    library(libs.strata.adapter.database.jdbi) {
        relocate("org{}jdbi", "me.whereareiam.identica.library.jdbi")
        relocate("io{}leangen{}geantyref", "me.whereareiam.identica.library.geantyref")
    }

    library(libs.strata.adapter.database.dialectica) {
        // Dialectica comes from the bundle, in the version Identica declares.
        excludeTransitive("me.whereareiam", "dialectica")
        excludeTransitive("me.whereareiam", "dialectica-api")
        excludeTransitive("me.whereareiam", "dialectica-common")
    }

    library(libs.jdbi.core) {
        relocate("org{}jdbi", "me.whereareiam.identica.library.jdbi")
        relocate("io{}leangen{}geantyref", "me.whereareiam.identica.library.geantyref")
    }

    library(libs.jdbi.sqlobject) {
        relocate("org{}jdbi", "me.whereareiam.identica.library.jdbi")
        relocate("io{}leangen{}geantyref", "me.whereareiam.identica.library.geantyref")
    }

    // One group per DatabaseType, named in lower case: only the configured database's driver is downloaded.
    group("postgres") {
        library(libs.postgresql) {
            excludeTransitive("com.github.waffle", "waffle-jna")
            excludeTransitive("net.java.dev.jna", "jna")
            excludeTransitive("net.java.dev.jna", "jna-platform")
        }
    }

    group("mysql") {
        library(libs.mariadb) {
            excludeTransitive("com.github.waffle", "waffle-jna")
            excludeTransitive("net.java.dev.jna", "jna")
            excludeTransitive("net.java.dev.jna", "jna-platform")
        }
    }

    group("sqlite") {
        library(libs.sqlite)
    }

    group("h2") {
        library(libs.h2)
    }
}
