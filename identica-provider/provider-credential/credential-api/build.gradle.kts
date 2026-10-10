plugins {
    id("module-java")
}

dependencies {
    compileOnly(projects.identicaApi)

    testImplementation(projects.identicaApi)
}
