plugins {
    base
}

tasks.register("cryptographyModules") {
    group = "build"
    description = "Builds all password provider cryptography modules."

    dependsOn(
        ":provider-password-cryptography-common:build",
        ":provider-password-cryptography-argon2:build",
        ":provider-password-cryptography-bcrypt:build"
    )
}
