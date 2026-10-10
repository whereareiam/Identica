plugins {
    id("module-java")
}

dependencies {
    compileOnly(projects.providerCredentialApi)

    testImplementation(projects.providerCredentialApi)
}
