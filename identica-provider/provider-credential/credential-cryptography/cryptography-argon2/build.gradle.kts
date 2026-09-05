plugins {
    id("shared")
}

dependencies {
    compileOnly(projects.providerCredentialApi)
    compileOnly(libs.argon2)

    testImplementation(projects.providerCredentialApi)
    testImplementation(libs.argon2)
}
