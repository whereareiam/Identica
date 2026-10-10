plugins {
    id("module-java")
}

dependencies {
    testImplementation(projects.identicaCommon)
    testImplementation(libs.keystone)
}
