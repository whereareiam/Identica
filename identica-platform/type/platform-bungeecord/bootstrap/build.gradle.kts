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

    attache(libs.adventure.platform.bungeecord)
    attache(libs.cloud.bungee)

    testRuntimeOnly(libs.strata)
    testRuntimeOnly(libs.strata.adapter.configura)
    testRuntimeOnly(libs.strata.adapter.database)
    testRuntimeOnly(libs.jedis)
    testRuntimeOnly(libs.hikaricp)
}

tasks.processResources {
    filesMatching("bungee.yml") {
        expand("version" to project.version)
    }
}
