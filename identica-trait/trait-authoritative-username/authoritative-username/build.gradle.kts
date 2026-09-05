plugins {
    id("api")
}

dependencies {
    api(projects.traitAuthoritativeUsernameApi)

    implementation(projects.traitAuthoritativeUsernameCommon)
    implementation(projects.traitAuthoritativeUsernameDatabase)
}

toolkitPublish {
    artifactId.set("username")
}

group = "me.whereareiam.identica.identity"
