plugins {
    base
}

tasks.register("platformModules") {
    group = "build"
    description = "Builds all Identica platform modules."

    dependsOn(
        ":platform-bungeecord:build",
        ":platform-velocity:build"
    )
}
