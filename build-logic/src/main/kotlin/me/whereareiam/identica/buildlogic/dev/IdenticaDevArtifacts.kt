package me.whereareiam.identica.buildlogic.dev

import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar

internal class IdenticaDevArtifacts(
	private val bundleJar: Any,
	private val credentialProviderJar: Any,
	private val premiumProviderJar: Any
) {
	fun installProxyArtifacts(pluginDir: String, install: (String, Any) -> Unit) {
		install(pluginDir, bundleJar)
		install("$pluginDir/providers", credentialProviderJar)
		install("$pluginDir/providers", premiumProviderJar)
	}

	companion object {
		fun create(project: Project): IdenticaDevArtifacts {
			project.evaluationDependsOn(":platform-bungeecord-bootstrap")
			project.evaluationDependsOn(":platform-velocity-bootstrap")
			project.evaluationDependsOn(":identica-platform:bundle")
			project.evaluationDependsOn(":provider-credential-runtime")
			project.evaluationDependsOn(":provider-premium-runtime")

			val bundleShadowJar = project.project(":identica-platform:bundle").tasks.named("shadowJar", Jar::class.java)
			val credentialShadowJar = project.project(":provider-credential-runtime").tasks.named("shadowJar", Jar::class.java)
			val premiumShadowJar = project.project(":provider-premium-runtime").tasks.named("shadowJar", Jar::class.java)

			return IdenticaDevArtifacts(
				bundleJar = bundleShadowJar.flatMap { it.archiveFile },
				credentialProviderJar = credentialShadowJar.flatMap { it.archiveFile },
				premiumProviderJar = premiumShadowJar.flatMap { it.archiveFile }
			)
		}
	}
}

internal fun Project.identicaDevArtifacts(): IdenticaDevArtifacts = IdenticaDevArtifacts.create(this)
