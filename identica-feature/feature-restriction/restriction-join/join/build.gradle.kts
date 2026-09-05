plugins {
    id("feature")
}

dependencies {
    api(projects.featureRestrictionJoinApi)

    implementation(projects.featureRestrictionJoinCommon)
}

toolkitPublish {
    artifactId.set("restriction-join")
}
