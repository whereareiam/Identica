import me.whereareiam.anvil.integration.junit.gradle.AnvilTest

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
	anvilImplementation(projects.exampleProvider)
	anvilImplementation(projects.identicaCommon)
	// The configuration directories and databases earlier versions of Identica left, which upgrade journeys start from.
	anvilImplementation(testFixtures(projects.identicaCommon))
	anvilImplementation(testFixtures(projects.identicaAdapterDatabaseCommon))
	anvilImplementation(testFixtures(projects.providerCredentialDatabase))
	anvilImplementation(testFixtures(projects.featureVerificationDatabase))
	anvilImplementation(projects.featureRecognitionCommon)
	anvilImplementation(projects.featureRestrictionJoinCommon)
	anvilImplementation(projects.featureSentinelApi)
	anvilImplementation(projects.featureSentinelCommon)
	anvilImplementation(projects.featureVerificationApi)
	anvilImplementation(projects.featureVerificationCommon)
	anvilImplementation(projects.providerCredentialApi)
	anvilImplementation(projects.providerCredentialCommon)
	anvilImplementation(projects.providerPremiumApi)
	anvilImplementation(projects.providerPremiumRuntime)
	anvilImplementation(libs.anvil.yggdrasil.mock)
	anvilImplementation(libs.commandant)
	anvilImplementation(libs.configura)
	anvilImplementation(libs.testcontainers.mariadb)
	anvilImplementation(libs.testcontainers.postgresql)
	anvilImplementation(libs.testcontainers.redis)

	anvilRuntimeOnly(libs.anvil.protocol.mcprotocol)
	// Testcontainers waits for a MariaDB container through its JDBC driver.
	anvilRuntimeOnly(libs.mariadb)

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
 * The plain jar of a project that bundles nothing, such as the example addon.
 */
fun projectJar(project: ProjectDependency): Configuration =
	configurations.detachedConfiguration(dependencies.project(project.path, "runtimeElements"))
		.apply { isTransitive = false }

/**
 * Journeys that run at once. Each one has its own network, so the default leaves four processors to every
 * network; a machine with fewer than eight runs one journey after another.
 */
val journeyParallelism = providers.gradleProperty("identica.journeys.parallelism").map(String::toInt)
	.orElse((Runtime.getRuntime().availableProcessors() / 4).coerceIn(1, 4))

/**
 * Suites the journeys are split into, each a JUnit tag: getting a player in, what happens to an account once
 * it exists, usernames and unique ids, what administrators do, two proxies sharing a database, and starting
 * from what an earlier version of Identica left.
 */
val journeySuites = listOf("join", "account", "identity", "operations", "cluster", "upgrade")

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
	artifact("example-provider", pluginJar(projects.exampleProvider))
	artifact("example-addon", projectJar(projects.exampleAddon))

	// Each suite runs the journeys of its tag in anvil<Suite>Test, such as anvilJoinTest.
	suites {
		journeySuites.forEach { suite ->
			register(suite) {
				includeTags(suite)
			}
		}
	}
}

// Left with what no suite claims, anvilTest checks that every journey belongs to exactly one suite.
tasks.named<AnvilTest>("anvilTest") {
	systemProperty("identica.journeys.suites", journeySuites.joinToString(","))
}

tasks.named<Test>("test") {
	useJUnitPlatform()
}

tasks.withType<AnvilTest>().configureEach {
	systemProperty("junit.jupiter.execution.parallel.enabled", "true")
	systemProperty("junit.jupiter.execution.parallel.mode.default", "concurrent")
	systemProperty("junit.jupiter.execution.parallel.config.strategy", "fixed")
	systemProperty("junit.jupiter.execution.parallel.config.fixed.parallelism", journeyParallelism.get())
	systemProperty("junit.jupiter.execution.parallel.config.fixed.max-pool-size", journeyParallelism.get())
}
