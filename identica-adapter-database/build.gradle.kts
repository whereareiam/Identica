import me.whereareiam.attache.plugin.gradle.extension.AttacheExtension

plugins {
    id("module-java")
    alias(libs.plugins.attache)
}

dependencies {
    attache(libs.dialectica)
    attache(libs.jdbi.core)
    attache(libs.jdbi.sqlobject)
    attache(libs.hikaricp)
    attache(libs.postgresql)
    attache(libs.mariadb)
    attache(libs.sqlite)
    attache(libs.h2)

    testImplementation(libs.bundles.database)
    testImplementation(libs.dialectica)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.mariadb)
}

extensions.configure<AttacheExtension>("attache") {
    library(libs.dialectica) {
        relocate("me{}whereareiam{}dialectica", "me.whereareiam.identica.library.dialectica")
        relocate("org{}jdbi", "me{}whereareiam{}identica{}library{}jdbi")
    }

    library(libs.jdbi.core) {
        relocate("org{}jdbi", "me.whereareiam.identica.library.jdbi")
        relocate("io{}leangen{}geantyref", "me.whereareiam.identica.library.geantyref")
    }

    library(libs.jdbi.sqlobject) {
        relocate("org{}jdbi", "me.whereareiam.identica.library.jdbi")
        relocate("io{}leangen{}geantyref", "me.whereareiam.identica.library.geantyref")
    }

    library(libs.postgresql) {
        excludeTransitive("com.github.waffle", "waffle-jna")
        excludeTransitive("net.java.dev.jna", "jna")
        excludeTransitive("net.java.dev.jna", "jna-platform")
    }

    library(libs.mariadb) {
        excludeTransitive("com.github.waffle", "waffle-jna")
        excludeTransitive("net.java.dev.jna", "jna")
        excludeTransitive("net.java.dev.jna", "jna-platform")
    }
}
