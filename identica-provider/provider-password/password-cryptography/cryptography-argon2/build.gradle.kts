plugins {
    id("identica.java-common")
}

dependencies {
    compileOnly(projects.providerPasswordApi)
    testImplementation(projects.providerPasswordApi)

    compileOnly(libs.argon2)
    testImplementation(libs.argon2)
}
