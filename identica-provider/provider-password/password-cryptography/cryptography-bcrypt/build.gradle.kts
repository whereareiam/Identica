plugins {
    id("identica.java-common")
}

dependencies {
    compileOnly(projects.providerPasswordApi)
    testImplementation(projects.providerPasswordApi)

    compileOnly(libs.bcrypt)
    testImplementation(libs.bcrypt)
}
