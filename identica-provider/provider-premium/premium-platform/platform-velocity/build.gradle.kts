plugins {
    id("shared")
}

dependencies {
    compileOnly(projects.platformVelocityApi)
    compileOnly(projects.providerPremiumApi)
    compileOnly(libs.velocity)

    testImplementation(projects.providerPremiumApi)
}
