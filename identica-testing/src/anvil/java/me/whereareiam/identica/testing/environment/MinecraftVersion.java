package me.whereareiam.identica.testing.environment;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Minecraft versions a network's backends run, each with the Paper build Anvil's live matrix runs. Players join with
 * the client of the same version, because Anvil connects a client only to servers of its own version.
 */
@Getter
@RequiredArgsConstructor
public enum MinecraftVersion {
	/**
	 * The version every journey runs on unless it names another.
	 */
	V1_21_11("1.21.11", "132"),
	/**
	 * A version before dialogs, which arrived in 1.21.6, for players whose client cannot show them.
	 */
	V1_21_1("1.21.1", "133");

	private final String version;
	private final String paperBuild;
}
