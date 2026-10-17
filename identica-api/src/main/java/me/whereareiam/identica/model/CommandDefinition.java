package me.whereareiam.identica.model;

import lombok.*;
import me.whereareiam.configura.annotation.PreserveUnknownFields;
import me.whereareiam.configura.feature.extension.api.annotation.ExtendableDocument;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configurable definition for one command entry in Identica's command documents.
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@ExtendableDocument
@PreserveUnknownFields
@Builder(toBuilder = true)
public class CommandDefinition {
	@Builder.Default
	private boolean enabled = true;
	private List<String> aliases;

	@Builder.Default
	private String permission = "";
	private String description;
	private String usage;

	private Cooldown cooldown;

	@Builder.Default
	private Map<String, String> arguments = new HashMap<>();
	@Builder.Default
	private boolean hide = false;

	/**
	 * Cooldown settings for a command definition: a command on cooldown refuses a player who ran it less than
	 * {@code duration} seconds ago and tells them how long to wait. The console is never refused.
	 */
	@Getter
	@ToString
	@NoArgsConstructor
	@AllArgsConstructor
	@ExtendableDocument
	@Builder(toBuilder = true)
	public static class Cooldown {
		/**
		 * Whether the command has a cooldown.
		 */
		@Builder.Default
		private boolean enabled = false;
		/**
		 * Seconds the command stays on cooldown after a player ran it; zero or less means no cooldown.
		 */
		@Builder.Default
		private int duration = 0;
		/**
		 * Name of the cooldown shared by every command with the same group; empty gives the command a cooldown of
		 * its own, shared only by its aliases.
		 */
		@Builder.Default
		private String group = "";
	}
}
