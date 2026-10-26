// Dialogs through BungeeCord's own dialog API.
plugins {
    id("module-addon")
}

dependencies {
    implementation(projects.dialogsCommon)

    compileOnly(libs.adventure.platform.bungeecord)
    compileOnly(libs.bungeecord)

    testImplementation(libs.adventure.platform.bungeecord)
    testImplementation(libs.bungeecord)
}

toolkitPublish {
    artifactId.set("dialogs-display-bungeecord")
}
