plugins {
    id("shared")
}

dependencies {
    compileOnly(projects.providerCredentialApi)

    testImplementation(projects.providerCredentialApi)
}
