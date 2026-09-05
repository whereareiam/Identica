plugins {
    id("shared")
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
