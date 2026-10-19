plugins {
    base
}

tasks.register("dialogsDisplayModules") {
    group = "build"
    description = "Builds all display modules of the dialogs addon."

    dependsOn(
        ":dialogs-display-bungeecord:build",
        ":dialogs-display-packetevents:build"
    )
}
