// Dialogs through PacketEvents, on every proxy it runs on; the proxy supplies PacketEvents at runtime.
plugins {
    id("module-addon")
}

dependencies {
    implementation(projects.dialogsCommon)

    compileOnly(libs.packetevents.api)

    testImplementation(libs.packetevents.api)
}

toolkitPublish {
    artifactId.set("dialogs-display-packetevents")
}
