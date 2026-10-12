import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("com.gradleup.shadow")
}

tasks.withType<ShadowJar>().configureEach {
    filesMatching("META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    mergeServiceFiles()

    relocate("com.google.inject", "me.whereareiam.identica.library.guice")
    relocate("com.google.common", "me.whereareiam.identica.library.guava")
    relocate("org.bstats", "me.whereareiam.identica.library.bstats")
    relocate("dev.faststats", "me.whereareiam.identica.library.faststats")
    relocate("me.whereareiam.statistica", "me.whereareiam.identica.library.statistica")
    relocate("org.jdbi", "me.whereareiam.identica.library.jdbi")
    relocate("com.fasterxml.jackson", "me.whereareiam.identica.library.jackson")
    relocate("org.yaml.snakeyaml", "me.whereareiam.identica.library.snakeyaml")
}
