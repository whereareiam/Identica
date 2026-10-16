import org.gradle.jvm.tasks.Jar

// The addon on this proxy alone, for stores that take one jar per proxy; the bundle ships both.
plugins {
    id("packaging-addon-jar")
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

tasks.named<Jar>("shadowJar").configure {
    archiveBaseName.set("Identica-Dialogs-BUNGEECORD")
}
