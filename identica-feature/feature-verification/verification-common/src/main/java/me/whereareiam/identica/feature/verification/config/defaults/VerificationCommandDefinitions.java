package me.whereareiam.identica.feature.verification.config.defaults;

import me.whereareiam.identica.model.CommandDefinition;

import java.util.List;
import java.util.Map;

public class VerificationCommandDefinitions {
	public void register(Registry registry) {
		registry.register(
				"verification",
				base("2fa", "Verification methods", "{alias}").toBuilder()
						.hide(true)
						.build()
		);
		registry.register("verification-status", base("2fa status", "Show verification status", "{alias}"));
		registry.register(
				"verification-enroll",
				withArguments(
						"2fa enroll",
						"Start verification method enrollment",
						"{alias} <method>",
						Map.of("method", "Method id")
				)
		);
		registry.register(
				"verification-confirm",
				withArguments(
						"2fa confirm",
						"Confirm a pending verification challenge",
						"{alias} <input>",
						Map.of("input", "Verification code")
				).toBuilder()
						.hide(true)
						.build()
		);
		registry.register(
				"verification-enroll-confirm",
				withArguments(
						"2fa enroll confirm",
						"Confirm pending verification enrollment",
						"{alias} <input>",
						Map.of("input", "Code or saved")
				).toBuilder()
						.hide(true)
						.build()
		);
		registry.register(
				"verification-use",
				withArguments(
						"2fa use",
						"Select a verification method for a provider",
						"{alias} <provider> <method>",
						Map.of("provider", "Provider id", "method", "Method id")
				)
		);
		registry.register(
				"verification-disable",
				withArguments(
						"2fa disable",
						"Disable an enrolled verification method",
						"{alias} <method>",
						Map.of("method", "Method id")
				)
		);
		registry.register(
				"verification-enroll-cancel",
				base("2fa enroll cancel", "Cancel pending verification enrollment", "{alias}").toBuilder()
						.hide(true)
						.build()
		);
		registry.register(
				"admin-verification-reset",
				withArguments(
						"identica admin 2fa reset",
						"Reset verification state for a player",
						"{alias} <target> [provider]",
						Map.of("target", "Player/UUID", "provider", "Provider id")
				).toBuilder().permission("identica.admin.2fa.reset").build()
		);
	}

	private CommandDefinition base(String alias, String description, String usage) {
		return CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of(alias))
				.permission("")
				.description(description)
				.usage(usage)
				.build();
	}

	private CommandDefinition withArguments(
			String alias,
			String description,
			String usage,
			Map<String, String> arguments
	) {
		return base(alias, description, usage).toBuilder()
				.arguments(arguments)
				.build();
	}

	@FunctionalInterface
	public interface Registry {
		void register(String key, CommandDefinition definition);
	}
}
