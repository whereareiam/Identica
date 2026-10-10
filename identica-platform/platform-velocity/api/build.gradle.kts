plugins {
    id("module-api")
}

group = "me.whereareiam.identica.platform.velocity"

dependencies {
    api(projects.identicaApi)
    compileOnly(libs.velocity)
}

toolkitPublish {
    artifactId.set("api")

    pom {
        description.set("Velocity-specific API for Identica")
        name.set("Identica Velocity API")
    }

    javadoc {
        title.set("Identica Velocity API")
        windowTitle.set("Identica Velocity API")
    }
}
