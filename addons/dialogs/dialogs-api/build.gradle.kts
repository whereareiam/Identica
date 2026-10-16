plugins {
    id("module-addon")
}

// Identica supplies its API at runtime: the addon compiles against it and never ships it.
dependencies {
    compileOnlyApi(projects.identicaApi)
}

toolkitPublish {
    artifactId.set("dialogs-api")
}
