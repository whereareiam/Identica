import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.jvm.tasks.Jar

plugins {
    id("identica.java-common")
    id("com.gradleup.shadow")
}

tasks.withType<ShadowJar>().configureEach {
    archiveBaseName.set(rootProject.name)

    relocate("com.google.inject", "me.whereareiam.identica.library.guice")
    relocate("com.google.common", "me.whereareiam.identica.library.guava")
    relocate("org.bstats", "me.whereareiam.identica.library.bstats")
    relocate("org.jdbi", "me.whereareiam.identica.library.jdbi")
    relocate("me.whereareiam.dialectica", "me.whereareiam.identica.library.dialectica")
    relocate("com.fasterxml.jackson", "me.whereareiam.identica.library.jackson")
    relocate("org.yaml.snakeyaml", "me.whereareiam.identica.library.snakeyaml")

    val defaultDestination = rootProject.layout.buildDirectory.dir("libs")

    if (providers.gradleProperty("output").isPresent) {
        destinationDirectory.set(file(providers.gradleProperty("output").get()))
    } else {
        destinationDirectory.set(defaultDestination)
    }
}

tasks.named<Jar>("jar").configure {
    dependsOn(tasks.named("shadowJar"))
}
