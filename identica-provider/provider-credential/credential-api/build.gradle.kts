plugins {
    id("shared")
}

dependencies {
    compileOnly(projects.identicaApi)

    testImplementation(projects.identicaApi)
}
