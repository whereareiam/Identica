plugins {
    base
}

tasks.register("addonModules") {
    group = "build"
    description = "Builds all official Identica addons."

    dependsOn(":addon-dialogs:dialogsModules")
}
