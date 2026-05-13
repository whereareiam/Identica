import org.gradle.jvm.tasks.Jar

plugins {
    id("identica.shadow-runtime")
}

dependencies {
    implementation(projects.providerPremiumApi)
    implementation(projects.providerPremiumPlatformBungeecord)
    implementation(projects.providerPremiumPlatformVelocity)
    testImplementation(projects.providerPremiumApi)
    testImplementation(projects.providerPremiumPlatformBungeecord)
    testImplementation(projects.providerPremiumPlatformVelocity)
}

tasks.named<Jar>("shadowJar").configure {
    archiveBaseName.set("Premium")
    archiveClassifier.set("")
}
