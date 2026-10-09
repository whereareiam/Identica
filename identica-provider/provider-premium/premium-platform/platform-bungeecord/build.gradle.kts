plugins {
    id("shared")
}

dependencies {
    compileOnly(projects.platformBungeecordApi)
    compileOnly(projects.providerPremiumApi)
    compileOnly(libs.bungeecord)

    testImplementation(projects.providerPremiumApi)
    testImplementation(libs.bungeecord)
}
