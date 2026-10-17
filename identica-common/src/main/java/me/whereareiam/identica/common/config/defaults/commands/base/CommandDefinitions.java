package me.whereareiam.identica.common.config.defaults.commands.base;

import me.whereareiam.identica.model.CommandDefinition;
import org.jetbrains.annotations.NotNull;

public interface CommandDefinitions {
	void register(@NotNull Registry registry);

	/**
	 * Cooldown of a command of its own: the command refuses a sender who ran it in the last two seconds.
	 */
	default CommandDefinition.Cooldown defaultCooldown() {
		return CommandDefinition.Cooldown.builder()
				.enabled(true)
				.duration(2)
				.build();
	}

	interface Registry {
		void register(@NotNull String key, @NotNull CommandDefinition definition);
	}
}
