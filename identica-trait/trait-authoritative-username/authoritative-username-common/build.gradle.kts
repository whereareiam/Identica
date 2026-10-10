plugins {
    id("module-java")
    id("packaging-publication")
}

dependencies {
    api(projects.traitAuthoritativeUsernameApi)
}

toolkitPublish {
    artifactId.set("username-common")
}

group = "me.whereareiam.identica.identity"
