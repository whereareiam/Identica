plugins {
    id("shared")
}

dependencies {
    compileOnly(projects.providerCredentialApi)
    compileOnly(libs.bcrypt)

    testImplementation(projects.providerCredentialApi)
    testImplementation(libs.bcrypt)
}
