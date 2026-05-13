plugins {
    base
}

tasks.register("passwordProviderModules") {
    group = "build"
    description = "Builds all password provider modules."

    dependsOn(
        ":provider-password-api:build",
        ":provider-password-common:build",
        ":provider-password-cryptography:build",
        ":provider-password-database:build",
        ":provider-password-runtime:build"
    )
}
