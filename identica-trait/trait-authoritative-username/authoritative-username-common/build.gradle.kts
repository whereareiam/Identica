plugins {
    id("api")
}

dependencies {
    api(projects.traitAuthoritativeUsernameApi)
}

toolkitPublish {
    artifactId.set("username-common")
}

group = "me.whereareiam.identica.identity"
