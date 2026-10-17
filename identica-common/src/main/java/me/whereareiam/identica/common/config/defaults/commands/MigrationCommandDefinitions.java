package me.whereareiam.identica.common.config.defaults.commands;

import me.whereareiam.identica.common.config.defaults.commands.base.CommandDefinitions;
import me.whereareiam.identica.model.CommandDefinition;

import java.util.List;
import java.util.Map;

public class MigrationCommandDefinitions implements CommandDefinitions {
	@Override
	public void register(Registry registry) {
		registry.register("migration-list", definition("migration list", "identica.admin.migration.list", "List provider links for a player", "<target>", Map.of("target", "Player/UUID")));
		registry.register("migration-start", definition("migration start", "identica.admin.migration.start", "Start provider migration for a player", "<target> <provider>", Map.of("target", "Player/UUID", "provider", "Provider id")));
		registry.register("migration-cancel", definition("migration cancel", "identica.admin.migration.cancel", "Cancel provider migration for a player", "<target>", Map.of("target", "Player/UUID")));
		registry.register("migration-primary", definition("migration primary", "identica.admin.migration.primary", "Set primary provider for a player", "<target> <provider>", Map.of("target", "Player/UUID", "provider", "Provider id")));
		registry.register("migration-drop", definition("migration drop", "identica.admin.migration.drop", "Drop provider link for a player", "<target> <provider>", Map.of("target", "Player/UUID", "provider", "Provider id")));
	}

	private CommandDefinition definition(
			String alias,
			String permission,
			String description,
			String argumentsUsage,
			Map<String, String> arguments
	) {
		return CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of(alias))
				.permission(permission)
				.description(description)
				.usage("{command} {alias} " + argumentsUsage)
				.arguments(arguments)
				.cooldown(defaultCooldown())
				.build();
	}
}
