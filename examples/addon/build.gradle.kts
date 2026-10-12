// An addon is an ordinary Velocity plugin that compiles against Identica's API and the libraries of its database API;
// Identica provides them at runtime, so the addon's plain jar bundles and relocates nothing.
plugins {
    java
}

description = "Example Velocity addon built on Identica's API"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

dependencies {
    compileOnly(projects.identicaAdapterDatabaseApi)
    compileOnly(projects.identicaApi)
    compileOnly(libs.dialectica)
    compileOnly(libs.dialectica.adapter.jdbi)
    compileOnly(libs.velocity)

    annotationProcessor(libs.velocity)
}
