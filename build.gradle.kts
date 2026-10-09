import me.whereareiam.attache.plugin.gradle.extension.AttacheExtension
import me.whereareiam.toolkit.versioning.extension.ToolkitVersioningExtension

plugins {
    alias(libs.plugins.attache)
    alias(libs.plugins.toolkit.versioning)
    alias(libs.plugins.spawner)
    id("dev-scenarios")
}

allprojects {
    version = rootProject.extensions
        .getByType(ToolkitVersioningExtension::class.java)
        .resolvedVersion()
        .get()
}

extensions.configure<AttacheExtension>("attache") {
    transitive.set(true)

    mavenLocal()
    repository("https://maven.whereareiam.me/release")
    repository("https://maven.whereareiam.me/development")
}

defaultTasks("pluginJars")

tasks.register("pluginJars") {
    group = "build"
    description = "Builds the proxy bootstrap jars and all bundled provider jars."

    dependsOn(
        ":platform-bungeecord-bootstrap:shadowJar",
        ":platform-velocity-bootstrap:shadowJar",
        ":identica-platform:bundle:shadowJar",
        ":provider-credential-runtime:shadowJar",
        ":provider-premium-runtime:shadowJar"
    )
}

val validatePipelineStructure = tasks.register("validatePipelineStructure") {
    group = "verification"
    description = "Validates pipeline package layout conventions."

    doLast {
        val errors = mutableListOf<String>()
        val mainSources = fileTree(projectDir) {
            include("identica-*/**/src/main/java/**/*.java")
            exclude("**/build/**")
        }

        mainSources.files.sortedBy { it.invariantSeparatorsPath }.forEach { file ->
            val path = file.invariantSeparatorsPath
            val text = file.readText()

            if ("/pipeline/scenario/base/" in path) {
                errors += "Top-level scenario/base is forbidden: $path"
            }

            if (path.contains("/pipeline/") && path.contains("/base/") &&
                !Regex("""\babstract\s+class\b""").containsMatchIn(text)
            ) {
                errors += "Only abstract classes may live in base packages: $path"
            }

            val pipelineParticipantOutsidePipeline =
                listOf("/identica-provider/", "/identica-feature/", "/identica-trait/").any(path::contains) &&
                        !path.contains("/pipeline/") &&
                        listOf(
                            "implements PipelineExtension",
                            "implements PipelinePhase<",
                            "extends InteractiveStep",
                            "extends SeamlessStep"
                        ).any(text::contains)

            if (pipelineParticipantOutsidePipeline) {
                errors += "Pipeline-participating provider/feature/trait class must live under pipeline/: $path"
            }
        }

        if (errors.isNotEmpty()) {
            throw GradleException(buildString {
                appendLine("Pipeline structure validation failed:")
                errors.forEach { appendLine(" - $it") }
            })
        }
    }
}

subprojects {
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(validatePipelineStructure)
    }
}
