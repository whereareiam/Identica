plugins {
	java
	id("me.whereareiam.anvil")
	id("me.whereareiam.anvil.junit")
	id("me.whereareiam.anvil.capability.default")
	id("me.whereareiam.anvil.platform.paper")
	id("me.whereareiam.anvil.platform.velocity")
}

description = "Live player journeys against packaged Identica artifacts"

java {
	toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

val anvilVersion = providers.gradleProperty("anvilVersion").get()

dependencies {
	add("anvilCompileOnly", libs.lombok)

	add("anvilAnnotationProcessor", libs.lombok)

	testImplementation(libs.junit.jupiter)

	testRuntimeOnly(libs.junit.platform)

	add("anvilRuntimeOnly", "me.whereareiam.anvil:protocol-mcprotocol:$anvilVersion")
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
