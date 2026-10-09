package me.whereareiam.identica.common.config.defaults.commands;

import me.whereareiam.identica.common.config.defaults.commands.base.CommandDefinitions;
import me.whereareiam.identica.model.CommandDefinition;

import java.util.List;
import java.util.Map;

public class AdminCommandDefinitions implements CommandDefinitions {
	@Override
	public void register(Registry registry) {
		registry.register("admin", CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("admin"))
				.permission("identica.admin")
				.description("Admin commands")
				.usage("{command} {alias}")
				.cooldown(globalCooldown())
				.build());

		registry.register("admin-reload", CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("admin reload"))
				.permission("identica.admin")
				.description("Reload command")
				.usage("{command} {alias}")
				.cooldown(globalCooldown())
				.build());

		registerClear(registry);
		registerDelete(registry);
		registerReservation(registry);
		registerSessions(registry);
	}

	private void registerClear(Registry registry) {
		registry.register("admin-clear", CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("admin clear"))
				.permission("identica.admin.clear")
				.description("Clear account data while preserving UUID")
				.usage("{command} {alias} <target>")
				.arguments(Map.of("target", "Player/UUID"))
				.cooldown(globalCooldown())
				.build());

		registry.register("admin-clear-confirm", CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("admin clear confirm"))
				.permission("identica.admin.clear")
				.description("Confirm account clear")
				.usage("{command} {alias}")
				.hide(true)
				.build());

		registry.register("admin-clear-cancel", CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("admin clear cancel"))
				.permission("identica.admin.clear")
				.description("Cancel account clear")
				.usage("{command} {alias}")
				.hide(true)
				.build());
	}

	private void registerDelete(Registry registry) {
		registry.register("admin-delete", CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("admin delete"))
				.permission("identica.admin.delete")
				.description("Delete account data and UUID")
				.usage("{command} {alias} <target>")
				.arguments(Map.of("target", "Player/UUID"))
				.cooldown(globalCooldown())
				.build());

		registry.register("admin-delete-confirm", CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("admin delete confirm"))
				.permission("identica.admin.delete")
				.description("Confirm account delete")
				.usage("{command} {alias}")
				.hide(true)
				.build());

		registry.register("admin-delete-cancel", CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("admin delete cancel"))
				.permission("identica.admin.delete")
				.description("Cancel account delete")
				.usage("{command} {alias}")
				.hide(true)
				.build());
	}

	private void registerReservation(Registry registry) {
		registry.register("admin-reservation-set", CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("admin reservation set"))
				.permission("identica.admin.reservation")
				.description("Create or update an account reservation")
				.usage("{command} {alias} <key> <uniqueId>")
				.arguments(Map.of("key", "Reservation key", "uniqueId", "UUID"))
				.cooldown(globalCooldown())
				.build());

		registry.register("admin-reservation-info", CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("admin reservation info"))
				.permission("identica.admin.reservation")
				.description("Show an account reservation")
				.usage("{command} {alias} <key>")
				.arguments(Map.of("key", "Reservation key"))
				.cooldown(globalCooldown())
				.build());

		registry.register("admin-reservation-delete", CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("admin reservation delete"))
				.permission("identica.admin.reservation")
				.description("Delete an account reservation")
				.usage("{command} {alias} <key>")
				.arguments(Map.of("key", "Reservation key"))
				.cooldown(globalCooldown())
				.build());
	}

	private void registerSessions(Registry registry) {
		registry.register("admin-session", CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("admin session"))
				.permission("identica.admin.sessions")
				.description("List active sessions")
				.usage("{command} {alias} [page]")
				.arguments(Map.of("page", "Page"))
				.hide(true)
				.cooldown(globalCooldown())
				.build());

		registry.register("admin-session-list", CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("admin session list"))
				.permission("identica.admin.sessions")
				.description("List active sessions")
				.usage("{command} {alias} [page]")
				.arguments(Map.of("page", "Page"))
				.cooldown(globalCooldown())
				.build());

		registry.register("admin-session-info", CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("admin session info"))
				.permission("identica.admin.sessions.info")
				.description("Show session details")
				.usage("{command} {alias} <target>")
				.arguments(Map.of("target", "Player/UUID"))
				.cooldown(globalCooldown())
				.build());

		registry.register("admin-session-end", CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("admin session end"))
				.permission("identica.admin.sessions.end")
				.description("End an active session")
				.usage("{command} {alias} <target>")
				.arguments(Map.of("target", "Player/UUID"))
				.cooldown(globalCooldown())
				.build());
	}

}
