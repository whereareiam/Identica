plugins {
    id("module-api")
}

group = "me.whereareiam.identica.platform.bungeecord"

dependencies {
    api(projects.identicaApi)
    compileOnly(libs.bungeecord)
}

toolkitPublish {
    artifactId.set("api")

    pom {
        description.set("BungeeCord-specific API for Identica")
        name.set("Identica BungeeCord API")
    }

    javadoc {
        title.set("Identica BungeeCord API")
        windowTitle.set("Identica BungeeCord API")
    }
}
