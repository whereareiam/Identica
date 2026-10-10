import me.whereareiam.attache.plugin.gradle.extension.AttacheExtension

plugins {
    id("module-java")
    alias(libs.plugins.attache)
}

dependencies {
    implementation(libs.attache.standalone)
    attache(libs.guice)
    attache(libs.configura)
    attache(libs.configura.feature.extension)
    attache(libs.configura.feature.postprocess)
    attache(libs.configura.feature.polymorphic)
    attache(libs.commandant)
    attache(libs.keystone)
    attache(libs.strata)
    attache(libs.strata.adapter.configura)

    compileOnly(libs.strata)
    compileOnly(libs.strata.adapter.configura)

    testImplementation(libs.keystone)
    testImplementation(libs.commandant)
    testImplementation(libs.strata)
    testImplementation(libs.strata.adapter.configura)
}

extensions.configure<AttacheExtension>("attache") {
    library(libs.strata) {
        relocate("me{}whereareiam{}strata", "me.whereareiam.identica.library.strata")
    }

    library(libs.strata.adapter.configura) {
        relocate("me{}whereareiam{}strata", "me.whereareiam.identica.library.strata")
        relocate("com{}fasterxml{}jackson", "me.whereareiam.identica.library.jackson")
        relocate("org{}yaml{}snakeyaml", "me.whereareiam.identica.library.snakeyaml")
    }

    library(libs.guice) {
        relocate("com{}google{}inject", "me.whereareiam.identica.library.guice")
        relocate("com{}google{}common", "me.whereareiam.identica.library.guava")
    }

    library(libs.configura) {
        relocate("com{}fasterxml{}jackson", "me.whereareiam.identica.library.jackson")
        relocate("org{}yaml{}snakeyaml", "me.whereareiam.identica.library.snakeyaml")
    }

    library(libs.configura.feature.extension) {
        relocate("com{}fasterxml{}jackson", "me.whereareiam.identica.library.jackson")
        relocate("org{}yaml{}snakeyaml", "me.whereareiam.identica.library.snakeyaml")
    }

    library(libs.configura.feature.postprocess) {
        relocate("com{}fasterxml{}jackson", "me.whereareiam.identica.library.jackson")
        relocate("org{}yaml{}snakeyaml", "me.whereareiam.identica.library.snakeyaml")
    }

    library(libs.configura.feature.polymorphic) {
        relocate("com{}fasterxml{}jackson", "me.whereareiam.identica.library.jackson")
        relocate("org{}yaml{}snakeyaml", "me.whereareiam.identica.library.snakeyaml")
    }
}
