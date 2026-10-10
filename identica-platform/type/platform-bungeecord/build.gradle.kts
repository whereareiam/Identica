plugins {
    base
}

tasks.register("bungeecordPlatform") {
    group = "build"
    description = "Builds all BungeeCord platform modules."

    dependsOn(
        ":platform-bungeecord-api:build",
        ":platform-bungeecord-bootstrap:build"
    )
}
