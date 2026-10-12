plugins {
    base
}

tasks.register("dialogsModules") {
    group = "build"
    description = "Builds all modules of the dialogs addon."

    dependsOn(
        ":dialogs-api:build",
        ":dialogs-common:build",
        ":dialogs-platform:dialogsPlatformModules"
    )
}
