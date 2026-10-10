plugins {
    id("module-java")
}

dependencies {
    compileOnly(projects.providerCredentialApi)
    compileOnly(libs.bcrypt)

    testImplementation(projects.providerCredentialApi)
    testImplementation(libs.bcrypt)
}
