plugins {
    id("identica.java-common")
}

java {
    withSourcesJar()
    withJavadocJar()
}

group = "me.whereareiam.identica.platform.bungeecord"

dependencies {
    api(projects.identicaApi)
    compileOnly(libs.bungeecord)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "api"
            pom {
                name.set("Identica BungeeCord API")
                description.set("BungeeCord-specific API for Identica")
            }
        }
    }
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("Xdoclint:none", "-quiet")
        title = "Identica BungeeCord API"
        windowTitle = "Identica BungeeCord API"
    }
}
