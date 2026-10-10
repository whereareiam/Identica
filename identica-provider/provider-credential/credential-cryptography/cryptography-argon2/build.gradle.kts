plugins {
    id("module-java")
}

dependencies {
    compileOnly(projects.providerCredentialApi)
    compileOnly(libs.argon2)

    testImplementation(projects.providerCredentialApi)
    testImplementation(libs.argon2)
}
