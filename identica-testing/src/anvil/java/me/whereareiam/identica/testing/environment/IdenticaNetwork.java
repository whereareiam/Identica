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
 * Builds the network Identica runs in: Velocity or BungeeCord proxies with the packaged plugin, the official
 * providers and the example provider, optionally the example addon, an auth server where players stay during
 * authentication and registration steps, and a lobby they reach afterwards.
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
	/**
	 * A server with a whitelist nobody is on, so it refuses every player. A network only has it when a proxy routes
	 * players there, such as with {@code @Identica(complete = CLOSED)}.
	 */
	public static final String CLOSED = "closed";
	/**
	 * Hostname every Velocity proxy forces to {@code lobby}; players join through another one unless they choose
	 * this one. Anvil writes a BungeeCord proxy's listener itself, without forced hosts.
	 */
	public static final String LOBBY_HOST = "lobby.identica.test";

	/**
	 * Builds a network of proxies that share the {@code auth} and {@code lobby} servers, each of which also runs
	 * the example addon when asked to. Players join through the first proxy unless they choose another one.
	 *
	 * @param name scenario name
	 * @param platform platform every proxy runs on
	 * @param proxies the files of each proxy, by its name
	 * @param sessionServer session server the proxies verify online players against; BungeeCord only verifies
	 * against Mojang's
	 * @param addon whether every proxy runs the example addon next to Identica
	 */
	public static AnvilScenario of(
			String name,
			Platform platform,
			Map<String, ProxyConfiguration> proxies,
			URI sessionServer,
			boolean addon
	) {
		return of(name, platform, proxies, Map.of(), sessionServer, addon);
	}

	/**
	 * Builds a network in which some proxies have a step server of their own. Such a proxy registers its own step
	 * server instead of {@code auth}, so players waiting at a step through it never meet the ones waiting through
	 * another proxy; every proxy shares {@code lobby}.
	 *
	 * @param stepServers the step server of each proxy that has its own, by the proxy's name
	 */
	public static AnvilScenario of(
			String name,
			Platform platform,
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
				.forEach(server -> scenario.server(backend(server).build()));
		scenario.server(backend(LOBBY).build());

		boolean closed = proxies.values().stream().anyMatch(configuration -> configuration.routesTo(CLOSED));
		if (closed)
			scenario.server(closedBackend());
		proxies.forEach((proxy, configuration) -> scenario.proxy(proxy(proxy, platform, configuration,
				stepServers.getOrDefault(proxy, AUTH), sessionServer, addon, closed)));

		return scenario.build();
	}

	private static MinecraftProxy proxy(
			String name,
			Platform platform,
			ProxyConfiguration configuration,
			String stepServer,
			URI sessionServer,
			boolean addon,
			boolean closed
	) {
		String data = platform.getData();
		WorkspacePlan.WorkspacePlanBuilder workspace = WorkspacePlan.builder()
				.asset(artifact("identica", Path.of("plugins", "identica.jar")))
				.asset(artifact("credential", Path.of(data, "providers", "credential.jar")))
				.asset(artifact("premium", Path.of(data, "providers", "premium.jar")))
				.asset(artifact("example-provider", Path.of(data, "providers", "example.jar")))
				.cache(libraries("identica-libraries", Path.of(data, ".libraries")))
				.cache(libraries("identica-provider-libraries", Path.of(data, "providers", ".libraries")));
		if (addon)
			workspace.asset(artifact("example-addon", Path.of("plugins", "identica-example-addon.jar")));
		configuration.files().forEach((file, content) -> workspace.asset(WorkspaceAsset.builder()
				.group("identica-configuration")
				.source(AssetSource.text(content))
				.target(Path.of(data, file))
				.build()));
		configuration.plugins().forEach((file, artifact) -> workspace.asset(artifact(artifact, Path.of("plugins", file))));
		configuration.seeds().forEach((file, source) -> workspace.asset(WorkspaceAsset.builder()
				.group("identica-seed")
				.source(AssetSource.path(source))
				.target(Path.of(data, file))
				.mode(AssetInstallMode.SEED_ONCE)
				.build()));

		MinecraftProxy.MinecraftProxyBuilder proxy = MinecraftProxy.builder()
				.name(name)
				.workspace(workspace.build())
				.server(stepServer)
				.server(LOBBY)
				.defaultServer(LOBBY);
		if (closed)
			proxy.server(CLOSED);
		if (platform == Platform.BUNGEECORD)
			return bungeeCord(proxy);

		return velocity(proxy, sessionServer);
	}

	private static MinecraftProxy velocity(MinecraftProxy.MinecraftProxyBuilder proxy, URI sessionServer) {
		return proxy.platform(Platforms.VELOCITY)
				.distribution(Distribution.remote("3.5.1", "615"))
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
	 * BungeeCord has no command rate limit, and Anvil cannot point it at another session server, so the proxy keeps
	 * Mojang's and only lets offline players in.
	 */
	private static MinecraftProxy bungeeCord(MinecraftProxy.MinecraftProxyBuilder proxy) {
		return proxy.platform(Platforms.BUNGEECORD)
				.distribution(Distribution.remote("BungeeCord", "2085"))
				// Journeys reconnect at once, which BungeeCord's default throttle of four seconds would refuse.
				.setting("connection_throttle", "0")
				.build();
	}

	/**
	 * A backend only has to be there for players to stand on, so it keeps running for the test run and serves one
	 * network after another instead of starting for every test.
	 */
	private static MinecraftServer.MinecraftServerBuilder backend(String name) {
		return MinecraftServer.builder()
				.name(name)
				.platform(Platforms.PAPER)
				.distribution(Distribution.remote("1.21.11", "132"))
				.memoryMegabytes(768)
				.lifetime(ProcessLifetime.ENGINE);
	}

	/**
	 * Its whitelist stays empty, so the server refuses every player while they connect, as a whitelisted server
	 * refuses a player who is not on it.
	 */
	private static MinecraftServer closedBackend() {
		return backend(CLOSED)
				.setting("white-list", "true")
				.setting("enforce-whitelist", "true")
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
