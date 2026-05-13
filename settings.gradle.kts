rootProject.name = "Identica"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")

    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.whereareiam.me/release")
        maven("https://maven.whereareiam.me/development")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)

    repositories {
        mavenCentral()
        maven("https://maven.whereareiam.me/release")
        maven("https://maven.whereareiam.me/development")
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
    }
}

include(":identica-api")
project(":identica-api").projectDir = file("identica-api")

include(":identica-common")
project(":identica-common").projectDir = file("identica-common")

include(":identica-engine")
project(":identica-engine").projectDir = file("identica-engine")

include(":identica-adapter-command")
project(":identica-adapter-command").projectDir = file("identica-adapter-command")

include(":identica-adapter-database")
project(":identica-adapter-database").projectDir = file("identica-adapter-database")

include(":identica-adapter-replication")
project(":identica-adapter-replication").projectDir = file("identica-adapter-replication")

include(":identica-integration")
project(":identica-integration").projectDir = file("identica-integration")

include(":integration-bstats")
project(":integration-bstats").projectDir = file("identica-integration/integration-bstats")

include(":identica-platform")
project(":identica-platform").projectDir = file("identica-platform")

include(":platform-velocity")
project(":platform-velocity").projectDir = file("identica-platform/platform-velocity")

include(":platform-bungeecord")
project(":platform-bungeecord").projectDir = file("identica-platform/platform-bungeecord")

include(":platform-velocity-api")
project(":platform-velocity-api").projectDir = file("identica-platform/platform-velocity/api")

include(":platform-bungeecord-api")
project(":platform-bungeecord-api").projectDir = file("identica-platform/platform-bungeecord/api")

include(":platform-velocity-bootstrap")
project(":platform-velocity-bootstrap").projectDir = file("identica-platform/platform-velocity/bootstrap")

include(":platform-bungeecord-bootstrap")
project(":platform-bungeecord-bootstrap").projectDir = file("identica-platform/platform-bungeecord/bootstrap")

include(":identica-provider")
project(":identica-provider").projectDir = file("identica-provider")

include(":provider-password")
project(":provider-password").projectDir = file("identica-provider/provider-password")

include(":provider-password-runtime")
project(":provider-password-runtime").projectDir = file("identica-provider/provider-password/password")

include(":provider-password-common")
project(":provider-password-common").projectDir = file("identica-provider/provider-password/password-common")

include(":provider-password-api")
project(":provider-password-api").projectDir = file("identica-provider/provider-password/password-api")

include(":provider-password-cryptography")
project(":provider-password-cryptography").projectDir = file("identica-provider/provider-password/password-cryptography")

include(":provider-password-cryptography-common")
project(":provider-password-cryptography-common").projectDir =
    file("identica-provider/provider-password/password-cryptography/common")

include(":provider-password-cryptography-argon2")
project(":provider-password-cryptography-argon2").projectDir =
    file("identica-provider/provider-password/password-cryptography/cryptography-argon2")

include(":provider-password-cryptography-bcrypt")
project(":provider-password-cryptography-bcrypt").projectDir =
    file("identica-provider/provider-password/password-cryptography/cryptography-bcrypt")

include(":provider-password-database")
project(":provider-password-database").projectDir = file("identica-provider/provider-password/password-database")

include(":provider-premium")
project(":provider-premium").projectDir = file("identica-provider/provider-premium")

include(":provider-premium-runtime")
project(":provider-premium-runtime").projectDir = file("identica-provider/provider-premium/premium")

include(":provider-premium-api")
project(":provider-premium-api").projectDir = file("identica-provider/provider-premium/premium-api")

include(":provider-premium-platform")
project(":provider-premium-platform").projectDir = file("identica-provider/provider-premium/premium-platform")

include(":provider-premium-platform-velocity")
project(":provider-premium-platform-velocity").projectDir =
    file("identica-provider/provider-premium/premium-platform/platform-velocity")

include(":provider-premium-platform-bungeecord")
project(":provider-premium-platform-bungeecord").projectDir =
    file("identica-provider/provider-premium/premium-platform/platform-bungeecord")
