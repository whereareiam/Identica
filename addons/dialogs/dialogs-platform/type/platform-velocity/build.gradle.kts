import org.gradle.jvm.tasks.Jar

// The addon on this proxy alone, for stores that take one jar per proxy; the bundle ships both.
plugins {
    id("packaging-addon-jar")
}

dependencies {
    implementation(projects.dialogsCommon)

    compileOnly(libs.packetevents.api)
    compileOnly(libs.velocity)

    annotationProcessor(libs.velocity)

    testImplementation(libs.packetevents.api)
    testImplementation(libs.velocity)
}

tasks.named<Jar>("shadowJar").configure {
    archiveBaseName.set("Identica-Dialogs-VELOCITY")
}
