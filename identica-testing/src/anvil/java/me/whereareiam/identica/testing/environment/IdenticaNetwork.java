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

import java.net.URI;
import java.nio.file.Path;
import java.util.Map;

/**
 * Builds the network Identica runs in: Velocity proxies with the packaged plugin and providers, an auth
 * server where players stay during authentication and registration steps, and a lobby they reach afterwards.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class IdenticaNetwork {
	public static final String PROXY = "proxy";
	/** The first proxy of a cluster, through which players join unless they choose another one. */
	public static final String PROXY_A = "proxy-a";
	public static final String PROXY_B = "proxy-b";
	public static final String AUTH = "auth";
	public static final String LOBBY = "lobby";

	/** Identica's data directory inside the proxy workspace. */
	public static final String DATA = "plugins/identica";

	/**
	 * Builds a network of Velocity proxies that share the {@code auth} and {@code lobby} servers. Players join
	 * through the first proxy unless they choose another one.
	 *
	 * @param name scenario name
	 * @param proxies configuration files by path inside Identica's data directory, for each proxy name
	 * @param sessionServer session server the proxies verify online players against
	 */
	public static AnvilScenario velocity(String name, Map<String, Map<String, String>> proxies, URI sessionServer) {
		AnvilScenario.AnvilScenarioBuilder scenario = AnvilScenario.builder()
				.name(name)
				.entrypoint(proxies.keySet().iterator().next())
				.server(backend(AUTH))
				.server(backend(LOBBY));
		proxies.forEach((proxy, configuration) -> scenario.proxy(proxy(proxy, configuration, sessionServer)));

		return scenario.build();
	}

	private static MinecraftProxy proxy(String name, Map<String, String> configuration, URI sessionServer) {
		WorkspacePlan.WorkspacePlanBuilder workspace = WorkspacePlan.builder()
				.asset(artifact("identica", Path.of("plugins", "identica.jar")))
				.asset(artifact("credential", Path.of(DATA, "providers", "credential.jar")))
				.asset(artifact("premium", Path.of(DATA, "providers", "premium.jar")))
				.cache(libraries("identica-libraries", Path.of(DATA, ".libraries")))
				.cache(libraries("identica-provider-libraries", Path.of(DATA, "providers", ".libraries")));
		configuration.forEach((file, content) -> workspace.asset(WorkspaceAsset.builder()
				.group("identica-configuration")
				.source(AssetSource.text(content))
				.target(Path.of(DATA, file))
				.build()));

		return MinecraftProxy.builder()
				.name(name)
				.platform(Platforms.VELOCITY)
				.distribution(Distribution.remote("3.5.1", "615"))
				.workspace(workspace.build())
				.server(AUTH)
				.server(LOBBY)
				.defaultServer(LOBBY)
				.setting("advanced.login-ratelimit", "0")
				// Journeys type their answers at once. Velocity's command rate limit would drop such a command, or with
				// its default forwarding hand it to the backend, before Identica sees it.
				.setting("advanced.command-rate-limit", "0")
				.setting("advanced.forward-commands-if-rate-limited", "false")
				.sessionServer(sessionServer)
				.build();
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
}
