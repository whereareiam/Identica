plugins {
    id("module-api")
    alias(libs.plugins.buildconfig)
}

group = "me.whereareiam.identica"

buildConfig {
    packageName("me.whereareiam.identica")

    // Add basic project info
    buildConfigField("String", "NAME", "\"${rootProject.name}\"")
    buildConfigField("String", "VERSION", "\"${rootProject.version}\"")

    // Automatically expose all versions from the version catalog
    val catalog = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")
    catalog.versionAliases.forEach { alias ->
        val version = catalog.findVersion(alias).get().toString()
        // Convert alias to valid Java constant name (e.g., "adventure-platform-bukkit" -> "ADVENTURE_PLATFORM_BUKKIT")
        // Replace both dashes and dots with underscores
        val fieldName = alias.replace("-", "_").replace(".", "_").uppercase()
        buildConfigField("String", fieldName, "\"$version\"")
    }
}

toolkitPublish {
    artifactId.set("api")

    pom {
        description.set("Public API for Identica - Modular authorization plugin")
        name.set("Identica API")
    }

    javadoc {
        title.set("Identica API")
        windowTitle.set("Identica API")
    }
}
