package me.whereareiam.identica.provider.premium.config.defaults;

import com.google.inject.Singleton;
import me.whereareiam.configura.merge.defaults.DefaultsProvider;
import me.whereareiam.identica.model.CommandDefinition;
import me.whereareiam.identica.provider.premium.config.PremiumCommands;

import java.util.List;
import java.util.Map;

@Singleton
public class PremiumCommandsDefaults implements DefaultsProvider<PremiumCommands> {
	@Override
	public PremiumCommands supply(PremiumCommands commands) {
		CommandDefinition premium = CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("premium"))
				.permission("")
				.description("Premium account migration")
				.usage("{alias}")
				.cooldown(CommandDefinition.Cooldown.builder()
						.enabled(true)
						.duration(2)
						.build())
				.build();

		CommandDefinition premiumConfirm = CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("premium confirm"))
				.permission("")
				.description("Confirm premium migration")
				.usage("{alias} [input]")
				.arguments(Map.of("input", "Code"))
				.hide(true)
				.build();

		CommandDefinition premiumCancel = CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("premium cancel"))
				.permission("")
				.description("Cancel premium migration")
				.usage("{alias}")
				.hide(true)
				.build();

		commands.getCommands().put("premium", premium);
		commands.getCommands().put("premium-confirm", premiumConfirm);
		commands.getCommands().put("premium-cancel", premiumCancel);
		return commands;
	}
}
