plugins {
    id("identica.java-common")
}

dependencies {
    compileOnly(projects.providerPremiumApi)
    testImplementation(projects.providerPremiumApi)

    compileOnly(projects.platformBungeecordApi)
    compileOnly(libs.bungeecord)
}
