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
	anvilImplementation(projects.featureRecognitionCommon)
	anvilImplementation(projects.featureSentinelApi)
	anvilImplementation(projects.featureSentinelCommon)
	anvilImplementation(projects.providerCredentialApi)
	anvilImplementation(projects.providerCredentialCommon)
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

/**
 * Journeys that run at once. Each one has its own network, so the default leaves four processors to every
 * network; a machine with fewer than eight runs one journey after another.
 */
val journeyParallelism = providers.gradleProperty("identica.journeys.parallelism").map(String::toInt)
	.orElse((Runtime.getRuntime().availableProcessors() / 4).coerceIn(1, 4))

anvil {
	acceptEula()
	engine {
		protocolLibrary("mcprotocol")
		// A server sizes its thread pools from the processors it sees; all of them at once stall the machine.
		processors.set(4)
		// Lets the test processes yield to whatever else the machine is doing. Windows has no nice.
		if (!System.getProperty("os.name").lowercase().contains("win")) processPriority.set("low")
	}

	artifact("identica", pluginJar(projects.identicaPlatform.bundle))
	artifact("credential", pluginJar(projects.providerCredentialRuntime))
	artifact("premium", pluginJar(projects.providerPremiumRuntime))
}

tasks.withType<Test>().configureEach {
	useJUnitPlatform()
}

tasks.named<Test>("anvilTest") {
	systemProperty("junit.jupiter.execution.parallel.enabled", "true")
	systemProperty("junit.jupiter.execution.parallel.mode.default", "concurrent")
	systemProperty("junit.jupiter.execution.parallel.config.strategy", "fixed")
	systemProperty("junit.jupiter.execution.parallel.config.fixed.parallelism", journeyParallelism.get())
	systemProperty("junit.jupiter.execution.parallel.config.fixed.max-pool-size", journeyParallelism.get())
}
