plugins {
    id("module-feature")
}

dependencies {
    api(projects.featureRestrictionApi)
}

toolkitPublish {
    artifactId.set("restriction-common")
}
