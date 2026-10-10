plugins {
    base
}

tasks.register("velocityPlatform") {
    group = "build"
    description = "Builds all Velocity platform modules."

    dependsOn(
        ":platform-velocity-api:build",
        ":platform-velocity-bootstrap:build"
    )
}
