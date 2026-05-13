import org.gradle.jvm.tasks.Jar

val velocityJvmArgs = listOf("-Xms256M", "-Xmx256M")
val bungeecordJvmArgs = listOf("-Xms256M", "-Xmx256M")
val paperJvmArgs = listOf("-Xms512M", "-Xmx512M")

evaluationDependsOn(":platform-bungeecord-bootstrap")
evaluationDependsOn(":platform-velocity-bootstrap")
evaluationDependsOn(":provider-password-runtime")
evaluationDependsOn(":provider-premium-runtime")

val bungeecordShadowJar = project(":platform-bungeecord-bootstrap").tasks.named("shadowJar", Jar::class.java)
val velocityShadowJar = project(":platform-velocity-bootstrap").tasks.named("shadowJar", Jar::class.java)
val passwordShadowJar = project(":provider-password-runtime").tasks.named("shadowJar", Jar::class.java)
val premiumShadowJar = project(":provider-premium-runtime").tasks.named("shadowJar", Jar::class.java)

val spawner = extensions.getByName("spawner")
(spawner.readProperty("serverDir") as DirectoryProperty).set(layout.projectDirectory.dir("dev/server"))
val scenarios = spawner.readProperty("scenarios")

scenarios.registerScenario("normal-velocity") { scenario ->
    scenario.addVelocity("proxy") { velocity ->
        velocity.setInt("port", 25565)
        velocity.setJvmArgs(velocityJvmArgs)
        velocity.setBoolean("onlineMode", false)
        velocity.setString("forwardingMode", "legacy")
        velocity.setDirectory("rootOverlayDir", "dev/scenarios/normal/velocity/proxy")
        velocity.addServer("lobby", "127.0.0.1:25566")
        velocity.setTryServers("lobby")
        velocity.addInstall("plugins", velocityShadowJar.flatMap { it.archiveFile })
        velocity.addInstall("plugins/identica/providers", passwordShadowJar.flatMap { it.archiveFile })
        velocity.addInstall("plugins/identica/providers", premiumShadowJar.flatMap { it.archiveFile })
    }

    scenario.addPaper("lobby") { paper ->
        paper.setInt("port", 25566)
        paper.setJvmArgs(paperJvmArgs)
        paper.setBoolean("onlineMode", false)
    }
}

scenarios.registerScenario("normal-bungeecord") { scenario ->
    scenario.addBungeeCord("proxy") { bungeecord ->
        bungeecord.setString("downloadProvider", "spigot-jenkins")
        bungeecord.setInt("port", 25565)
        bungeecord.setJvmArgs(bungeecordJvmArgs)
        bungeecord.setBoolean("onlineMode", false)
        bungeecord.setBoolean("ipForward", true)
        bungeecord.setDirectory("rootOverlayDir", "dev/scenarios/normal/bungeecord/proxy")
        bungeecord.addServer("lobby", "127.0.0.1:25566")
        bungeecord.setTryServers("lobby")
        bungeecord.addInstall("plugins", bungeecordShadowJar.flatMap { it.archiveFile })
        bungeecord.addInstall("plugins/identica/providers", passwordShadowJar.flatMap { it.archiveFile })
        bungeecord.addInstall("plugins/identica/providers", premiumShadowJar.flatMap { it.archiveFile })
    }

    scenario.addPaper("lobby") { paper ->
        paper.setInt("port", 25566)
        paper.setJvmArgs(paperJvmArgs)
        paper.setBoolean("onlineMode", false)
    }
}

scenarios.registerScenario("extended-velocity") { scenario ->
    scenario.addVelocity("proxy") { velocity ->
        velocity.setInt("port", 25565)
        velocity.setJvmArgs(velocityJvmArgs)
        velocity.setBoolean("onlineMode", false)
        velocity.setString("forwardingMode", "legacy")
        velocity.setDirectory("rootOverlayDir", "dev/scenarios/extended/velocity/proxy")
        velocity.addServer("auth", "127.0.0.1:25566")
        velocity.addServer("migration", "127.0.0.1:25567")
        velocity.addServer("registration", "127.0.0.1:25568")
        velocity.addServer("lobby", "127.0.0.1:25569")
        velocity.setTryServers("lobby")
        velocity.addInstall("plugins", velocityShadowJar.flatMap { it.archiveFile })
        velocity.addInstall("plugins/identica/providers", passwordShadowJar.flatMap { it.archiveFile })
        velocity.addInstall("plugins/identica/providers", premiumShadowJar.flatMap { it.archiveFile })
    }

    scenario.addPaper("auth") { paper ->
        paper.setInt("port", 25566)
        paper.setJvmArgs(paperJvmArgs)
        paper.setBoolean("onlineMode", false)
        paper.setDirectory("rootOverlayDir", "dev/scenarios/extended/auth")
    }

    scenario.addPaper("migration") { paper ->
        paper.setInt("port", 25567)
        paper.setJvmArgs(paperJvmArgs)
        paper.setBoolean("onlineMode", false)
        paper.setDirectory("rootOverlayDir", "dev/scenarios/extended/migration")
    }

    scenario.addPaper("registration") { paper ->
        paper.setInt("port", 25568)
        paper.setJvmArgs(paperJvmArgs)
        paper.setBoolean("onlineMode", false)
        paper.setDirectory("rootOverlayDir", "dev/scenarios/extended/registration")
    }

    scenario.addPaper("lobby") { paper ->
        paper.setInt("port", 25569)
        paper.setJvmArgs(paperJvmArgs)
        paper.setBoolean("onlineMode", false)
        paper.setDirectory("rootOverlayDir", "dev/scenarios/extended/lobby")
    }
}

scenarios.registerScenario("extended-bungeecord") { scenario ->
    scenario.addBungeeCord("proxy") { bungeecord ->
        bungeecord.setString("downloadProvider", "spigot-jenkins")
        bungeecord.setInt("port", 25565)
        bungeecord.setJvmArgs(bungeecordJvmArgs)
        bungeecord.setBoolean("onlineMode", false)
        bungeecord.setBoolean("ipForward", true)
        bungeecord.setDirectory("rootOverlayDir", "dev/scenarios/extended/bungeecord/proxy")
        bungeecord.addServer("auth", "127.0.0.1:25566")
        bungeecord.addServer("migration", "127.0.0.1:25567")
        bungeecord.addServer("registration", "127.0.0.1:25568")
        bungeecord.addServer("lobby", "127.0.0.1:25569")
        bungeecord.setTryServers("lobby")
        bungeecord.addInstall("plugins", bungeecordShadowJar.flatMap { it.archiveFile })
        bungeecord.addInstall("plugins/identica/providers", passwordShadowJar.flatMap { it.archiveFile })
        bungeecord.addInstall("plugins/identica/providers", premiumShadowJar.flatMap { it.archiveFile })
    }

    scenario.addPaper("auth") { paper ->
        paper.setInt("port", 25566)
        paper.setJvmArgs(paperJvmArgs)
        paper.setBoolean("onlineMode", false)
        paper.setDirectory("rootOverlayDir", "dev/scenarios/extended/auth")
    }

    scenario.addPaper("migration") { paper ->
        paper.setInt("port", 25567)
        paper.setJvmArgs(paperJvmArgs)
        paper.setBoolean("onlineMode", false)
        paper.setDirectory("rootOverlayDir", "dev/scenarios/extended/migration")
    }

    scenario.addPaper("registration") { paper ->
        paper.setInt("port", 25568)
        paper.setJvmArgs(paperJvmArgs)
        paper.setBoolean("onlineMode", false)
        paper.setDirectory("rootOverlayDir", "dev/scenarios/extended/registration")
    }

    scenario.addPaper("lobby") { paper ->
        paper.setInt("port", 25569)
        paper.setJvmArgs(paperJvmArgs)
        paper.setBoolean("onlineMode", false)
        paper.setDirectory("rootOverlayDir", "dev/scenarios/extended/lobby")
    }
}

private fun Any.registerScenario(name: String, configure: (Any) -> Unit) {
    javaClass.getMethod("register", String::class.java, Action::class.java)
        .invoke(
            this,
            name,
            object : Action<Any> {
                override fun execute(scenario: Any) = configure(scenario)
            }
        )
}

private fun Any.addVelocity(name: String, configure: (Any) -> Unit) {
    javaClass.getMethod("velocity", String::class.java, Action::class.java)
        .invoke(
            this,
            name,
            object : Action<Any> {
                override fun execute(velocity: Any) = configure(velocity)
            }
        )
}

private fun Any.addBungeeCord(name: String, configure: (Any) -> Unit) {
    javaClass.getMethod("bungeecord", String::class.java, Action::class.java)
        .invoke(
            this,
            name,
            object : Action<Any> {
                override fun execute(bungeecord: Any) = configure(bungeecord)
            }
        )
}

private fun Any.addPaper(name: String, configure: (Any) -> Unit) {
    javaClass.getMethod("paper", String::class.java, Action::class.java)
        .invoke(
            this,
            name,
            object : Action<Any> {
                override fun execute(paper: Any) = configure(paper)
            }
        )
}

@Suppress("UNCHECKED_CAST")
private fun Any.addInstall(into: String, source: Any) {
    javaClass.getMethod("install", Action::class.java)
        .invoke(
            this,
            object : Action<Any> {
                override fun execute(install: Any) {
                    install.javaClass.getMethod("from", Array<Any>::class.java).invoke(install, arrayOf(source))
                    (install.readProperty("into") as Property<String>).set(into)
                }
            }
        )
}

private fun Any.addServer(name: String, address: String) {
    javaClass.getMethod("server", String::class.java, String::class.java)
        .invoke(this, name, address)
}

private fun Any.setTryServers(vararg names: String) {
    javaClass.getMethod("tryServers", Array<String>::class.java).invoke(this, names)
}

private fun Any.setDirectory(propertyName: String, relativePath: String) {
    @Suppress("UNCHECKED_CAST")
    (readProperty(propertyName) as DirectoryProperty).set(layout.projectDirectory.dir(relativePath))
}

private fun Any.setInt(propertyName: String, value: Int) {
    @Suppress("UNCHECKED_CAST")
    (readProperty(propertyName) as Property<Int>).set(value)
}

private fun Any.setJvmArgs(value: List<String>) {
    @Suppress("UNCHECKED_CAST")
    (readProperty("jvmArgs") as ListProperty<String>).set(value)
}

private fun Any.setBoolean(propertyName: String, value: Boolean) {
    @Suppress("UNCHECKED_CAST")
    (readProperty(propertyName) as Property<Boolean>).set(value)
}

@Suppress("UNCHECKED_CAST")
private fun Any.setString(propertyName: String, value: String) {
    (readProperty(propertyName) as Property<String>).set(value)
}

private fun Any.readProperty(name: String): Any {
    val methodName = "get" + name.replaceFirstChar { it.uppercase() }
    return javaClass.getMethod(methodName).invoke(this)
}
