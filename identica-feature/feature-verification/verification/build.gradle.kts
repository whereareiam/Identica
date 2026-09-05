plugins {
    id("feature")
}

dependencies {
    api(projects.featureVerificationApi)

    implementation(projects.featureVerificationCommon)
    implementation(projects.featureVerificationDatabase)
}

toolkitPublish {
    artifactId.set("verification")
}
