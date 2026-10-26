plugins {
    id("module-addon")
}

dependencies {
    api(projects.dialogsApi)
}

toolkitPublish {
    artifactId.set("dialogs-common")
}
