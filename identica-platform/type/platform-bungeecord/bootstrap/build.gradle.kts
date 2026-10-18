plugins {
    id("packaging-platform-jar")
    alias(libs.plugins.attache)
}

platform {
    name.set("BUNGEECORD")
    descriptors.add("bungee.yml")
}

dependencies {
    testImplementation(libs.jdbi.core)
    testImplementation(libs.jdbi.sqlobject)
    testImplementation(libs.dialectica.bundle)
    testImplementation(libs.dialectica.adapter.jdbi)
    testImplementation(projects.identicaAdapterDatabaseApi)
    testImplementation(libs.commandant)
    testImplementation(libs.bungeecord)
    testImplementation(libs.cloud.bungee)
    testImplementation(libs.adventure.platform.bungeecord)

    implementation(projects.platformBungeecordApi)
    implementation(libs.statistica.platform.bungeecord) {
        capabilities { requireCapability("me.whereareiam:statistica-platform-bungeecord-bstats") }
    }
    implementation(libs.statistica.platform.bungeecord) {
        capabilities { requireCapability("me.whereareiam:statistica-platform-bungeecord-faststats") }
    }
    implementation(libs.attache.bungeecord)

    compileOnly(libs.bungeecord)

    testRuntimeOnly(libs.strata)
    testRuntimeOnly(libs.strata.adapter.configura)
    testRuntimeOnly(libs.strata.adapter.database)
    testRuntimeOnly(libs.jedis)
    testRuntimeOnly(libs.hikaricp)
}

// The bundle jar carries the descriptors of every platform, so each platform loads its own libraries as a group.
attache {
    group("bungeecord") {
        // BungeeCord has no Adventure, so Identica brings the version it compiles against.
        library(libs.adventure)
        library(libs.adventure.key)
        library(libs.adventure.nbt)
        library(libs.adventure.minimessage)
        library(libs.adventure.plain)
        library(libs.adventure.gson)
        library(libs.adventure.legacy)
        // Attache resolves each library's dependencies on its own, so the older Adventure that the platform
        // depends on would load next to the declared one.
        library(libs.adventure.platform.bungeecord) {
            excludeTransitive("net.kyori", "adventure-api")
            excludeTransitive("net.kyori", "adventure-key")
            excludeTransitive("net.kyori", "adventure-nbt")
            excludeTransitive("net.kyori", "adventure-text-serializer-gson")
            excludeTransitive("net.kyori", "adventure-text-serializer-legacy")
        }
        library(libs.cloud.bungee)
    }
}

tasks.processResources {
    filesMatching("bungee.yml") {
        expand("version" to project.version)
    }
}
