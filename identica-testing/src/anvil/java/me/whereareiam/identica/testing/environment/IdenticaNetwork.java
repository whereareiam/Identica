package me.whereareiam.identica.testing.environment;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.workspace.AssetSource;
import me.whereareiam.anvil.api.model.workspace.WorkspaceAsset;
import me.whereareiam.anvil.api.model.workspace.WorkspaceCache;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.api.type.CacheIdentity;
import me.whereareiam.anvil.api.type.Platforms;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Builds the network Identica runs in: one Velocity proxy with the packaged plugin and providers, an auth
 * server where players stay during authentication and registration steps, and a lobby they reach afterwards.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class IdenticaNetwork {
	public static final String PROXY = "proxy";
	public static final String AUTH = "auth";
	public static final String LOBBY = "lobby";

	private static final String DATA = "plugins/identica";

	public static AnvilScenario velocity(String name, Map<String, String> configuration) {
		WorkspacePlan.WorkspacePlanBuilder workspace = WorkspacePlan.builder()
				.asset(artifact("identica", Path.of("plugins", "identica.jar")))
				.asset(artifact("credential", Path.of(DATA, "providers", "credential.jar")))
				.asset(artifact("premium", Path.of(DATA, "providers", "premium.jar")))
				.cache(libraries("identica-libraries", Path.of(DATA, ".libraries")))
				.cache(libraries("identica-provider-libraries", Path.of(DATA, "providers", ".libraries")));
		Path overlays = overlayDirectory(name);
		configuration.forEach((file, content) -> workspace.asset(WorkspaceAsset.builder()
				.group("identica-configuration")
				.source(AssetSource.path(write(overlays.resolve(file), content)))
				.target(Path.of(DATA, file))
				.build()));

		MinecraftServer auth = backend(AUTH);
		MinecraftServer lobby = backend(LOBBY);
		MinecraftProxy proxy = MinecraftProxy.builder()
				.name(PROXY)
				.platform(Platforms.VELOCITY)
				.distribution(Distribution.remote("3.5.1", "615"))
				.workspace(workspace.build())
				.server(AUTH)
				.server(LOBBY)
				.defaultServer(LOBBY)
				.build();

		return AnvilScenario.builder().name(name).entrypoint(PROXY).server(auth).server(lobby).proxy(proxy).build();
	}

	private static MinecraftServer backend(String name) {
		return MinecraftServer.builder()
				.name(name)
				.platform(Platforms.PAPER)
				.distribution(Distribution.remote("1.21.11", "132"))
				.memoryMegabytes(768)
				.build();
	}

	/**
	 * Identica downloads its dependency libraries on first start and validates them itself, so a rebuilt plugin
	 * keeps them.
	 */
	private static WorkspaceCache libraries(String group, Path path) {
		return WorkspaceCache.builder().group(group).path(path).identity(CacheIdentity.PROCESS).build();
	}

	private static WorkspaceAsset artifact(String name, Path target) {
		return WorkspaceAsset.builder().group(name).source(AssetSource.artifact(name)).target(target).build();
	}

	private static Path overlayDirectory(String name) {
		return Path.of("build", "identica-overlays", name).toAbsolutePath();
	}

	private static Path write(Path file, String content) {
		try {
			Files.createDirectories(file.getParent());
			return Files.writeString(file, content);
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}
}
