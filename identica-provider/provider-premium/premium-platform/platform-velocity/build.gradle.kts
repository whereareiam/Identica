plugins {
    id("module-java")
}

dependencies {
    compileOnly(projects.platformVelocityApi)
    compileOnly(projects.providerPremiumApi)
    compileOnly(libs.velocity)

    testImplementation(projects.providerPremiumApi)
}
