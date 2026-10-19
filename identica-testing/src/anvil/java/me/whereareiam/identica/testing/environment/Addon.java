package me.whereareiam.identica.testing.environment;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Addons a test proxy can run next to Identica, with what each installs on the platforms it runs on.
 */
public enum Addon {
	/**
	 * The example addon the developer documentation builds, which holds back chat and commands on Velocity until a
	 * player has authenticated, and holds players at its checkpoints on Velocity and BungeeCord.
	 */
	EXAMPLE(Map.of(
			Platform.VELOCITY, new Installation("plugins/identica-example-addon", List.of(plugin("example-addon", "identica-example-addon.jar"))),
			Platform.BUNGEECORD, new Installation("plugins/IdenticaExampleAddon", List.of(plugin("example-addon", "identica-example-addon.jar")))
	)),
	/**
	 * The official dialogs addon. On Velocity it needs PacketEvents; BungeeCord shows dialogs itself and names the
	 * addon's data folder after its plugin name.
	 */
	DIALOGS(Map.of(
			Platform.VELOCITY, new Installation("plugins/identica-dialogs", List.of(
					plugin("packetevents", "packetevents.jar"),
					plugin("dialogs", "identica-dialogs.jar")
			)),
			Platform.BUNGEECORD, new Installation("plugins/Identica-Dialogs", List.of(plugin("dialogs", "identica-dialogs.jar")))
	));

	private final Map<Platform, Installation> installations;

	Addon(Map<Platform, Installation> installations) {
		this.installations = installations;
	}

	/**
	 * Returns what the addon installs on a platform.
	 *
	 * @param platform the proxy's platform
	 * @throws IllegalArgumentException when the addon does not run on that platform
	 */
	public Installation on(Platform platform) {
		Installation installation = installations.get(platform);
		if (installation == null) throw new IllegalArgumentException(name() + " does not run on " + platform);

		return installation;
	}

	private static Plugin plugin(String artifact, String jar) {
		return new Plugin(artifact, Path.of("plugins", jar));
	}

	/**
	 * The addon on one platform: its data folder inside the proxy workspace and the plugin jars it installs.
	 */
	public record Installation(String dataDirectory, List<Plugin> plugins) {
	}

	/**
	 * A plugin jar: the artifact the build registers and where the proxy loads it from.
	 */
	public record Plugin(String artifact, Path target) {
	}
}
