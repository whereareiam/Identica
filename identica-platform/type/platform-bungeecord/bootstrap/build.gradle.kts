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

    // BungeeCord has no Adventure, so Identica brings the version it compiles against.
    attache(libs.adventure)
    attache(libs.adventure.key)
    attache(libs.adventure.nbt)
    attache(libs.adventure.minimessage)
    attache(libs.adventure.plain)
    attache(libs.adventure.gson)
    attache(libs.adventure.legacy)
    attache(libs.adventure.platform.bungeecord)
    attache(libs.cloud.bungee)

    testRuntimeOnly(libs.strata)
    testRuntimeOnly(libs.strata.adapter.configura)
    testRuntimeOnly(libs.strata.adapter.database)
    testRuntimeOnly(libs.jedis)
    testRuntimeOnly(libs.hikaricp)
}

// Attache resolves each library's dependencies on its own, so the older Adventure that the platform depends on
// would load next to the declared one.
attache {
    library(libs.adventure.platform.bungeecord) {
        excludeTransitive("net.kyori", "adventure-api")
        excludeTransitive("net.kyori", "adventure-key")
        excludeTransitive("net.kyori", "adventure-nbt")
        excludeTransitive("net.kyori", "adventure-text-serializer-gson")
        excludeTransitive("net.kyori", "adventure-text-serializer-legacy")
    }
}

tasks.processResources {
    filesMatching("bungee.yml") {
        expand("version" to project.version)
    }
}
