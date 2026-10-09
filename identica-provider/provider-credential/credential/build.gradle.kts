import org.gradle.jvm.tasks.Jar

plugins {
    id("runtime")
}

dependencies {
    implementation(projects.providerCredentialApi)
    implementation(projects.providerCredentialCommon)
    implementation(projects.providerCredentialCryptographyArgon2)
    implementation(projects.providerCredentialCryptographyBcrypt)
    implementation(projects.providerCredentialCryptographyCommon)
    implementation(projects.providerCredentialDatabase)

    compileOnly(projects.featureRecognitionApi)
    compileOnly(projects.featureSentinelApi)
    compileOnly(projects.featureVerificationApi)

    testImplementation(projects.featureRecognitionApi)
    testImplementation(projects.featureSentinelApi)
    testImplementation(projects.featureVerificationApi)
    testImplementation(projects.providerCredentialApi)
}

tasks.named<Jar>("shadowJar").configure {
    archiveBaseName.set("Credential")
    archiveClassifier.set("")
}
