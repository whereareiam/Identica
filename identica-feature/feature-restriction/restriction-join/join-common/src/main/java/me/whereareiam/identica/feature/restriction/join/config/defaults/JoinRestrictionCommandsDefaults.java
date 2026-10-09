package me.whereareiam.identica.feature.restriction.join.config.defaults;

import com.google.inject.Singleton;
import me.whereareiam.configura.merge.defaults.DefaultsProvider;
import me.whereareiam.identica.model.CommandDefinition;
import me.whereareiam.identica.feature.restriction.join.config.JoinRestrictionCommands;

import java.util.List;
import java.util.Map;

@Singleton
public class JoinRestrictionCommandsDefaults implements DefaultsProvider<JoinRestrictionCommands> {
	@Override
	public JoinRestrictionCommands supply(JoinRestrictionCommands commands) {
		CommandDefinition enable = definition(
				"admin provider restriction join enable",
				"Enable configured provider join restriction",
				"{command} {alias} <provider>"
		);
		enable.setArguments(Map.of("provider", "Provider id"));

		CommandDefinition disable = definition(
				"admin provider restriction join disable",
				"Disable runtime provider join restriction",
				"{command} {alias} <provider>"
		);
		disable.setArguments(Map.of("provider", "Provider id"));

		CommandDefinition status = definition(
				"admin provider restriction join status",
				"Show provider join restriction status",
				"{command} {alias} [provider]"
		);
		status.setArguments(Map.of("provider", "Provider id"));

		commands.getCommands().put("admin-provider-restriction-join-enable", enable);
		commands.getCommands().put("admin-provider-restriction-join-disable", disable);
		commands.getCommands().put("admin-provider-restriction-join-status", status);
		return commands;
	}

	private CommandDefinition definition(String alias, String description, String usage) {
		return CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of(alias))
				.permission("identica.admin.provider.restriction")
				.description(description)
				.usage(usage)
				.cooldown(CommandDefinition.Cooldown.builder()
						.enabled(true)
						.duration(2)
						.group("global")
						.build()
				)
				.build();
	}
}
