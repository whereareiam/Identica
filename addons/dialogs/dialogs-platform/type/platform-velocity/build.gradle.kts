plugins {
    id("module-java")
}

dependencies {
    implementation(projects.dialogsCommon)
    implementation(projects.dialogsDisplayPacketevents)

    compileOnly(libs.packetevents.api)
    compileOnly(libs.velocity)

    annotationProcessor(libs.velocity)

    testImplementation(libs.packetevents.api)
    testImplementation(libs.velocity)
}
