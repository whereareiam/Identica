plugins {
    id("module-feature")
}

dependencies {
    api(projects.featureRestrictionApi)

    implementation(projects.featureRestrictionCommon)
}

toolkitPublish {
    artifactId.set("restriction")
}
