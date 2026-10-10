plugins {
	java
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
	// Identica's configuration models and defaults, to build and read the proxy's files through them.
	add("anvilImplementation", project(":identica-api"))
	add("anvilImplementation", project(":identica-common"))
	add("anvilImplementation", project(":provider-premium-api"))
	add("anvilImplementation", libs.anvil.yggdrasil.mock)
	add("anvilImplementation", libs.commandant)
	add("anvilImplementation", libs.configura)

	add("anvilCompileOnly", libs.lombok)

	add("anvilAnnotationProcessor", libs.lombok)

	testImplementation(libs.junit.jupiter)

	testRuntimeOnly(libs.junit.platform)

	add("anvilRuntimeOnly", libs.anvil.protocol.mcprotocol)
}

anvil {
	acceptEula()
	engine {
		protocolLibrary("mcprotocol")
	}

	mapOf(
		"identica" to ":identica-platform:bundle",
		"credential" to ":provider-credential-runtime",
		"premium" to ":provider-premium-runtime"
	).forEach { (name, module) ->
		evaluationDependsOn(module)
		artifact(name, project(module).tasks.named<Jar>("shadowJar").flatMap { it.archiveFile })
	}
}

tasks.withType<Test>().configureEach {
	useJUnitPlatform()
}
