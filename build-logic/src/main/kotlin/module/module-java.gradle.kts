plugins {
    `java-library`
    id("io.freefair.lombok")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

group = "me.whereareiam"

// Compilation and tests use Java 21 whichever Java runs Gradle.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    add("compileOnly", libs.findLibrary("guice").get())
    add("compileOnly", libs.findLibrary("annotations").get())
    add("compileOnly", libs.findLibrary("configura").get())
    add("compileOnly", libs.findLibrary("configura-feature-extension").get())
    add("compileOnly", libs.findLibrary("configura-feature-polymorphic").get())
    add("compileOnly", libs.findLibrary("configura-feature-postprocess").get())
    add("compileOnly", libs.findLibrary("commandant").get())
    add("compileOnly", libs.findLibrary("keystone").get())
    add("compileOnly", libs.findLibrary("dialectica").get())
    add("compileOnly", libs.findBundle("adventure").get())
    add("implementation", libs.findLibrary("attache-common").get())

    add("testRuntimeOnly", libs.findLibrary("junit-platform").get())
    add("testImplementation", libs.findLibrary("junit-jupiter").get())
    add("testImplementation", libs.findLibrary("guice").get())
    add("testImplementation", libs.findLibrary("annotations").get())
    add("testImplementation", libs.findLibrary("configura").get())
    add("testImplementation", libs.findLibrary("configura-feature-extension").get())
    add("testImplementation", libs.findLibrary("configura-feature-polymorphic").get())
    add("testImplementation", libs.findLibrary("configura-feature-postprocess").get())
    add("testImplementation", libs.findLibrary("keystone").get())
    add("testImplementation", libs.findBundle("adventure").get())
    add("testImplementation", libs.findLibrary("mockito-core").get())
    add("testImplementation", libs.findLibrary("mockito-junit").get())

    if (path != ":identica-api") {
        add("compileOnly", project(":identica-api"))
        add("testImplementation", project(":identica-api"))
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    ignoreFailures = providers.gradleProperty("ignoreTestFailures")
        .map(String::toBoolean)
        .orElse(false)
        .get()
}
