import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.jvm.tasks.Jar

// An addon's plugin jar: a plugin of its own that loads after Identica and uses Identica's classes, so it carries
// only the addon's modules and relocates nothing; its platform annotations, such as Velocity's @Inject, have to keep
// their names.
plugins {
    id("module-java")
    id("com.gradleup.shadow")
}

configurations.named("runtimeClasspath") {
    exclude(group = "me.whereareiam", module = "attache-common")
}

tasks.withType<ShadowJar>().configureEach {
    archiveClassifier.set("")
    mergeServiceFiles()

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
