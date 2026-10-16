package me.whereareiam.identica.testing.environment;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.type.Platforms;
import me.whereareiam.identica.type.platform.PlatformType;

import java.nio.file.Path;

/**
 * Proxy platform a test network runs Identica on. Velocity is the default; BungeeCord verifies premium logins
 * against Mojang's own session server, so its networks only let offline players in.
 */
@Getter
@RequiredArgsConstructor
public enum Platform {
	VELOCITY(Platforms.VELOCITY, PlatformType.VELOCITY, "plugins/identica"),
	/**
	 * BungeeCord names Identica's data directory after the plugin's name in {@code bungee.yml}.
	 */
	BUNGEECORD(Platforms.BUNGEECORD, PlatformType.BUNGEECORD, "plugins/Identica");

	/** Anvil's id of the platform. */
	private final String id;
	/** Identica's type of the platform. */
	private final PlatformType type;
	/** Identica's data directory inside the proxy workspace. */
	private final String data;

	/**
	 * Returns the platform a proxy of a running network runs on.
	 *
	 * @param anvil running network
	 * @param proxy name of the proxy
	 */
	public static Platform of(ScenarioContext anvil, String proxy) {
		String id = anvil.definition().getProxies().stream()
				.filter(declaration -> declaration.getName().equals(proxy))
				.map(MinecraftProxy::getPlatform)
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("The network has no proxy " + proxy));
		for (Platform platform : values())
			if (platform.id.equals(id)) return platform;

		throw new IllegalStateException("Proxy " + proxy + " runs on " + id + ", which the test network does not build");
	}

	/**
	 * Returns Identica's data directory of a proxy of a running network.
	 *
	 * @param anvil running network
	 * @param proxy name of the proxy
	 */
	public static Path dataDirectory(ScenarioContext anvil, String proxy) {
		return anvil.processes().proxy(proxy).workDirectory().resolve(of(anvil, proxy).data);
	}
}
