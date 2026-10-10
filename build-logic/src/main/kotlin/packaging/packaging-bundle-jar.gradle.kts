import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import me.whereareiam.identica.buildlogic.bundle.bundleDescriptors
import me.whereareiam.identica.buildlogic.bundle.bundlePlatformId
import me.whereareiam.identica.buildlogic.bundle.platformCollection

plugins {
    id("packaging-shaded-jar")
}

val platformCollection = platformCollection()

gradle.projectsEvaluated {
    val memberPaths = platformCollection.memberPaths.get().sorted()

    memberPaths.forEach { memberPath ->
        dependencies.add("implementation", project(memberPath))
    }

    tasks.named<ShadowJar>("shadowJar").configure {
        archiveClassifier.set("BUNDLE")

        memberPaths.forEach { memberPath ->
            val memberProject = rootProject.project(memberPath)
            memberProject.bundleDescriptors().forEach { descriptor ->
                from({
                    val memberJar = memberProject
                        .tasks
                        .named<ShadowJar>("shadowJar")
                        .flatMap { it.archiveFile }
                        .get()
                        .asFile

                    zipTree(memberJar).matching {
                        include(descriptor)
                    }
                })
            }
        }

        manifest {
            attributes(
                "Plugin-Type" to "BUNDLE",
                "Supported-Platforms" to memberPaths.joinToString(",") { memberPath ->
                    rootProject.project(memberPath).bundlePlatformId()
                }
            )
        }
    }
}
