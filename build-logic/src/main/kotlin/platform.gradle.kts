import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import me.whereareiam.identica.buildlogic.bundle.bundlePlatformId
import me.whereareiam.identica.buildlogic.bundle.platform
import me.whereareiam.identica.buildlogic.bundle.platformCollection

plugins {
    id("runtime")
}

val platformCollection = platformCollection()
val platform = platform()

dependencies {
    add("implementation", project(":identica-api"))
    add("implementation", project(":identica-common"))
    add("implementation", project(":identica-feature"))
    add("implementation", project(":trait-authoritative-username"))
    add("implementation", project(":identica-engine"))
    add("implementation", project(":identica-adapter-command"))
    add("implementation", project(":identica-adapter-database"))
    add("implementation", project(":identica-adapter-replication"))
    add("implementation", project(":integration-bstats"))
}

afterEvaluate {
    tasks.named<ShadowJar>("shadowJar").configure {
        archiveClassifier.set(bundlePlatformId())
    }

    if (platform.bundled.get())
        platformCollection.memberPaths.add(project.path)
}
