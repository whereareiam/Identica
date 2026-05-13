plugins {
    id("identica.java-common")
}

dependencies {
    compileOnly(projects.identicaApi)
    testImplementation(projects.identicaApi)
}
