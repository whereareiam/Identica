package me.whereareiam.identica.testing.environment;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.nio.file.Path;
import java.util.List;

/**
 * Addons a test proxy can run next to Identica, each with the plugin jars it installs.
 */
@Getter
@RequiredArgsConstructor
public enum Addon {
	/**
	 * The example addon the developer documentation builds, which holds back chat and commands until a player has
	 * authenticated.
	 */
	EXAMPLE("plugins/identica-example-addon", List.of(new Plugin("example-addon", Path.of("plugins", "identica-example-addon.jar")))),
	/**
	 * The official dialogs addon, with PacketEvents, which it needs on Velocity.
	 */
	DIALOGS("plugins/identica-dialogs", List.of(
			new Plugin("packetevents", Path.of("plugins", "packetevents.jar")),
			new Plugin("dialogs", Path.of("plugins", "identica-dialogs.jar"))
	));

	/**
	 * The addon's data folder inside the proxy workspace.
	 */
	private final String dataDirectory;
	private final List<Plugin> plugins;

	/**
	 * A plugin jar: the artifact the build registers and where the proxy loads it from.
	 */
	public record Plugin(String artifact, Path target) {
	}
}
