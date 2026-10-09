plugins {
    id("feature")
}

dependencies {
    api(projects.featureSentinelApi)

    implementation(projects.featureSentinelCommon)
}

toolkitPublish {
    artifactId.set("sentinel")
}
