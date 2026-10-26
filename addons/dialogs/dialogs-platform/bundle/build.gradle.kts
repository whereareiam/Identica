import org.gradle.jvm.tasks.Jar

// One plugin jar for both proxies: each platform module brings its entry point and descriptor, and a proxy loads
// only the one it knows.
plugins {
    id("packaging-addon-jar")
}

dependencies {
    implementation(projects.dialogsPlatformBungeecord)
    implementation(projects.dialogsPlatformVelocity)
}

tasks.named<Jar>("shadowJar").configure {
    archiveBaseName.set("Identica-Dialogs")
}
