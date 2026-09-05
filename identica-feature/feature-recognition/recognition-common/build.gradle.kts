plugins {
    id("feature")
}

dependencies {
    api(projects.featureRecognitionApi)
    api(projects.featureRestrictionApi)
    api(projects.featureRestrictionJoinApi)
}

toolkitPublish {
    artifactId.set("recognition-common")
}
