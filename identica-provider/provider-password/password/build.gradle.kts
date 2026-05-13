import org.gradle.jvm.tasks.Jar

plugins {
    id("identica.shadow-runtime")
}

dependencies {
    implementation(projects.providerPasswordApi)
    implementation(projects.providerPasswordCommon)
    implementation(projects.providerPasswordDatabase)
    implementation(projects.providerPasswordCryptographyCommon)
    implementation(projects.providerPasswordCryptographyBcrypt)
    implementation(projects.providerPasswordCryptographyArgon2)

    testImplementation(projects.providerPasswordApi)
}

tasks.named<Jar>("shadowJar").configure {
    archiveBaseName.set("Password")
    archiveClassifier.set("")
}
