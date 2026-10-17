package me.whereareiam.identica.common.config.defaults.commands;

import me.whereareiam.identica.common.config.defaults.commands.base.CommandDefinitions;
import me.whereareiam.identica.model.CommandDefinition;

import java.util.List;
import java.util.Map;

public class CoreCommandDefinitions implements CommandDefinitions {
	@Override
	public void register(Registry registry) {
		registry.register("main", CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("identica", "auth"))
				.permission("")
				.description("Main command")
				.usage("{alias}")
				.cooldown(defaultCooldown())
				.hide(true)
				.build());

		registry.register("help", CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("help"))
				.permission("")
				.description("Help command")
				.usage("{command} {alias} [page]")
				.arguments(Map.of("page", "Page"))
				.cooldown(defaultCooldown())
				.build());

		registry.register("enroll", CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("enroll"))
				.permission("")
				.description("Select authentication eligibility")
				.usage("{command} {alias} <eligibility>")
				.arguments(Map.of("eligibility", "Provider id"))
				.hide(true)
				.build());

		registry.register("availability-username", CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("availability username"))
				.permission("")
				.description("Check username availability")
				.usage("{command} {alias} <username>")
				.arguments(Map.of("username", "Username"))
				.cooldown(defaultCooldown())
				.build());
	}
}
