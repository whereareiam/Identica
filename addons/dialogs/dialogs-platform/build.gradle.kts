plugins {
    base
}

tasks.register("dialogsPlatformModules") {
    group = "build"
    description = "Builds all platform modules of the dialogs addon."

    dependsOn(
        ":dialogs-platform-bungeecord:build",
        ":dialogs-platform-velocity:build",
        ":dialogs-platform-bundle:build"
    )
}
