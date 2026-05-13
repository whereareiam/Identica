plugins {
    base
}

tasks.register("premiumPlatformModules") {
    group = "build"
    description = "Builds all premium provider platform modules."

    dependsOn(
        ":provider-premium-platform-bungeecord:build",
        ":provider-premium-platform-velocity:build"
    )
}
