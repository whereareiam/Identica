plugins {
	java
	alias(libs.plugins.freefair.lombok)
	alias(libs.plugins.anvil)
	alias(libs.plugins.anvil.junit)
	alias(libs.plugins.anvil.capability.default)
	alias(libs.plugins.anvil.platform.paper)
	alias(libs.plugins.anvil.platform.velocity)
}

description = "Live player journeys against packaged Identica artifacts"

java {
	toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

dependencies {
	// Identica's configuration and message models and defaults, to build and read the proxy's files through them.
	anvilImplementation(projects.identicaApi)
	anvilImplementation(projects.identicaCommon)
	anvilImplementation(projects.providerCredentialApi)
	anvilImplementation(projects.providerPremiumApi)
	anvilImplementation(projects.providerPremiumRuntime)
	anvilImplementation(libs.anvil.yggdrasil.mock)
	anvilImplementation(libs.commandant)
	anvilImplementation(libs.configura)
	anvilImplementation(libs.testcontainers.postgresql)
	anvilImplementation(libs.testcontainers.redis)

	anvilRuntimeOnly(libs.anvil.protocol.mcprotocol)

	testImplementation(libs.junit.jupiter)

	testRuntimeOnly(libs.junit.platform)
}

/**
 * The packaged jar of a plugin or provider project, built before the tests that install it.
 */
fun pluginJar(project: ProjectDependency): Configuration =
	configurations.detachedConfiguration(dependencies.project(project.path, "shadowRuntimeElements"))
		.apply { isTransitive = false }

anvil {
	acceptEula()
	engine {
		protocolLibrary("mcprotocol")
	}

	artifact("identica", pluginJar(projects.identicaPlatform.bundle))
	artifact("credential", pluginJar(projects.providerCredentialRuntime))
	artifact("premium", pluginJar(projects.providerPremiumRuntime))
}

tasks.withType<Test>().configureEach {
	useJUnitPlatform()
}
