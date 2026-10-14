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
import me.whereareiam.anvil.api.type.AssetInstallMode;
import me.whereareiam.anvil.api.type.CacheIdentity;
import me.whereareiam.anvil.api.type.Platforms;
import me.whereareiam.anvil.api.type.ProcessLifetime;

import java.net.URI;
import java.nio.file.Path;
import java.util.Map;

/**
 * Builds the network Identica runs in: Velocity proxies with the packaged plugin, the official providers and the
 * example provider, optionally the example addon, an auth
 * server where players stay during authentication and registration steps, and a lobby they reach afterwards.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class IdenticaNetwork {
	public static final String PROXY = "proxy";
	/** The first proxy of a cluster, through which players join unless they choose another one. */
	public static final String PROXY_A = "proxy-a";
	public static final String PROXY_B = "proxy-b";
	public static final String AUTH = "auth";
	/**
	 * The step server of proxy B in a cluster whose proxies each have their own, in place of {@code auth}.
	 */
	public static final String AUTH_B = "auth-b";
	public static final String LOBBY = "lobby";
	/** Hostname every proxy forces to {@code lobby}; players join through another one unless they choose this one. */
	public static final String LOBBY_HOST = "lobby.identica.test";

	/** Identica's data directory inside the proxy workspace. */
	public static final String DATA = "plugins/identica";

	/**
	 * Builds a network of Velocity proxies that share the {@code auth} and {@code lobby} servers. Players join
	 * through the first proxy unless they choose another one.
	 *
	 * @param name scenario name
	 * @param proxies the files of each proxy, by its name
	 * @param sessionServer session server the proxies verify online players against
	 */
	public static AnvilScenario velocity(String name, Map<String, ProxyConfiguration> proxies, URI sessionServer) {
		return velocity(name, proxies, Map.of(), sessionServer, false);
	}

	/**
	 * Builds a network of Velocity proxies, each of which also runs the example addon when asked to.
	 *
	 * @param addon whether every proxy runs the example addon next to Identica
	 */
	public static AnvilScenario velocity(String name, Map<String, ProxyConfiguration> proxies, URI sessionServer, boolean addon) {
		return velocity(name, proxies, Map.of(), sessionServer, addon);
	}

	/**
	 * Builds a network of Velocity proxies in which some proxies have a step server of their own. Such a proxy
	 * registers its own step server instead of {@code auth}, so players waiting at a step through it never meet the
	 * ones waiting through another proxy; every proxy shares {@code lobby}.
	 *
	 * @param stepServers the step server of each proxy that has its own, by the proxy's name
	 */
	public static AnvilScenario velocity(
			String name,
			Map<String, ProxyConfiguration> proxies,
			Map<String, String> stepServers,
			URI sessionServer,
			boolean addon
	) {
		AnvilScenario.AnvilScenarioBuilder scenario = AnvilScenario.builder()
				.name(name)
				.entrypoint(proxies.keySet().iterator().next());
		proxies.keySet().stream()
				.map(proxy -> stepServers.getOrDefault(proxy, AUTH))
				.distinct()
				.forEach(server -> scenario.server(backend(server)));
		scenario.server(backend(LOBBY));
		proxies.forEach((proxy, configuration) -> scenario.proxy(
				proxy(proxy, configuration, stepServers.getOrDefault(proxy, AUTH), sessionServer, addon)));

		return scenario.build();
	}

	private static MinecraftProxy proxy(String name, ProxyConfiguration configuration, String stepServer, URI sessionServer, boolean addon) {
		WorkspacePlan.WorkspacePlanBuilder workspace = WorkspacePlan.builder()
				.asset(artifact("identica", Path.of("plugins", "identica.jar")))
				.asset(artifact("credential", Path.of(DATA, "providers", "credential.jar")))
				.asset(artifact("premium", Path.of(DATA, "providers", "premium.jar")))
				.asset(artifact("example-provider", Path.of(DATA, "providers", "example.jar")))
				.cache(libraries("identica-libraries", Path.of(DATA, ".libraries")))
				.cache(libraries("identica-provider-libraries", Path.of(DATA, "providers", ".libraries")));
		if (addon)
			workspace.asset(artifact("example-addon", Path.of("plugins", "identica-example-addon.jar")));
		configuration.files().forEach((file, content) -> workspace.asset(WorkspaceAsset.builder()
				.group("identica-configuration")
				.source(AssetSource.text(content))
				.target(Path.of(DATA, file))
				.build()));
		configuration.seeds().forEach((file, source) -> workspace.asset(WorkspaceAsset.builder()
				.group("identica-seed")
				.source(AssetSource.path(source))
				.target(Path.of(DATA, file))
				.mode(AssetInstallMode.SEED_ONCE)
				.build()));

		return MinecraftProxy.builder()
				.name(name)
				.platform(Platforms.VELOCITY)
				.distribution(Distribution.remote("3.5.1", "615"))
				.workspace(workspace.build())
				.server(stepServer)
				.server(LOBBY)
				.defaultServer(LOBBY)
				.setting("advanced.login-ratelimit", "0")
				// Journeys type their answers at once. Velocity's command rate limit would drop such a command, or with
				// its default forwarding hand it to the backend, before Identica sees it.
				.setting("advanced.command-rate-limit", "0")
				.setting("advanced.forward-commands-if-rate-limited", "false")
				.setting("forced-hosts.\"" + LOBBY_HOST + "\"", "[\"" + LOBBY + "\"]")
				.sessionServer(sessionServer)
				.build();
	}

	/**
	 * A backend only has to be there for players to stand on, so it keeps running for the test run and serves one
	 * network after another instead of starting for every test.
	 */
	private static MinecraftServer backend(String name) {
		return MinecraftServer.builder()
				.name(name)
				.platform(Platforms.PAPER)
				.distribution(Distribution.remote("1.21.11", "132"))
				.memoryMegabytes(768)
				.lifetime(ProcessLifetime.ENGINE)
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
