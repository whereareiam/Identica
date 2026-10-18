// An addon is an ordinary proxy plugin that compiles against Identica's API and the libraries of its database API;
// Identica provides them at runtime, so the addon's plain jar bundles and relocates nothing. This jar is a Velocity
// plugin, and on BungeeCord registers the example's checkpoints.
plugins {
    java
}

description = "Example addon built on Identica's API"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

dependencies {
    compileOnly(projects.identicaAdapterDatabaseApi)
    compileOnly(projects.identicaApi)
    compileOnly(libs.bungeecord)
    compileOnly(libs.dialectica)
    compileOnly(libs.dialectica.adapter.jdbi)
    compileOnly(libs.velocity)

    annotationProcessor(libs.velocity)
}
