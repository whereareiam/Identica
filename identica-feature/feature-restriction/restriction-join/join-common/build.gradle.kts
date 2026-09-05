plugins {
    id("feature")
}

dependencies {
    api(projects.featureRestrictionJoinApi)

    compileOnly(projects.identicaEngine)

    testImplementation(projects.identicaEngine)
}

toolkitPublish {
    artifactId.set("restriction-join-common")
}
