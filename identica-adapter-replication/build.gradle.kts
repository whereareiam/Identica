plugins {
    id("module-java")
    alias(libs.plugins.attache)
}

dependencies {
    attache(libs.jedis)

    testImplementation(libs.jedis)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.redis)
}
