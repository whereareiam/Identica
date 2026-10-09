plugins {
    id("api")
}

group = "me.whereareiam.identica.feature"

dependencies {
    api(projects.identicaApi)
}

toolkitPublish {
    artifactId.set("verification-api")
}
