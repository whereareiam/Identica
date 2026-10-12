plugins {
    id("module-java")
}

dependencies {
    implementation(projects.dialogsCommon)

    compileOnly(libs.packetevents.api)
    compileOnly(libs.velocity)

    annotationProcessor(libs.velocity)

    testImplementation(libs.packetevents.api)
    testImplementation(libs.velocity)
}
