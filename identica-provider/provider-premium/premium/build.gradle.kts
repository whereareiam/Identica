import org.gradle.jvm.tasks.Jar

plugins {
    id("runtime")
}

dependencies {
    implementation(projects.providerPremiumApi)
    implementation(projects.providerPremiumPlatformBungeecord)
    implementation(projects.providerPremiumPlatformVelocity)

    compileOnly(projects.featureRecognitionApi)
    compileOnly(projects.featureVerificationApi)

    testImplementation(projects.featureRecognitionApi)
    testImplementation(projects.featureVerificationApi)
    testImplementation(projects.providerPremiumApi)
    testImplementation(projects.providerPremiumPlatformBungeecord)
    testImplementation(projects.providerPremiumPlatformVelocity)
}

tasks.named<Jar>("shadowJar").configure {
    archiveBaseName.set("Premium")
    archiveClassifier.set("")
}
