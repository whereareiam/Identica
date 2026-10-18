plugins {
    id("module-java")
    alias(libs.plugins.attache)
}

dependencies {
    testImplementation(libs.cloud.annotations)
    testImplementation(libs.cloud.cooldowns)
    testImplementation(libs.cloud.core)
    testImplementation(libs.cloud.minecraft.extras)
    testImplementation(libs.commandant)

    attache(libs.cloud.annotations)
    attache(libs.cloud.cooldowns)
    attache(libs.cloud.core)
    attache(libs.cloud.minecraft.extras)
}

// The platform supplies Adventure: the proxy on Velocity, the bootstrap on BungeeCord.
attache {
    library(libs.cloud.minecraft.extras) {
        excludeTransitive("net.kyori", "adventure-api")
        excludeTransitive("net.kyori", "adventure-key")
        excludeTransitive("net.kyori", "adventure-text-minimessage")
        excludeTransitive("net.kyori", "adventure-text-serializer-plain")
    }
}
