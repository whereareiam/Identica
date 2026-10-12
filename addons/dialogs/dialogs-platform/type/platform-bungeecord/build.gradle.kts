plugins {
    id("module-java")
}

dependencies {
    implementation(projects.dialogsCommon)

    compileOnly(libs.adventure.platform.bungeecord)
    compileOnly(libs.bungeecord)

    testImplementation(libs.adventure.platform.bungeecord)
    testImplementation(libs.bungeecord)
}

tasks.named<ProcessResources>("processResources") {
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("bungee.yml") {
        expand("version" to version)
    }
}
