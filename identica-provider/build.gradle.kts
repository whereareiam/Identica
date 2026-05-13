plugins {
    base
}

tasks.register("providerModules") {
    group = "build"
    description = "Builds all Identica provider modules."

    dependsOn(
        ":provider-password:build",
        ":provider-premium:build"
    )
}
