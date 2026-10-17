package me.whereareiam.identica.provider.credential.config.defaults;

import com.google.inject.Singleton;
import me.whereareiam.configura.merge.defaults.DefaultsProvider;
import me.whereareiam.identica.model.CommandDefinition;
import me.whereareiam.identica.provider.credential.config.CredentialCommands;

import java.util.List;
import java.util.Map;

@Singleton
public class CredentialCommandsDefaults implements DefaultsProvider<CredentialCommands> {
	@Override
	public CredentialCommands supply(CredentialCommands commands) {
		CommandDefinition register = CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("pass"))
				.permission("")
				.description("Register a credential account")
				.usage("{alias} <password>")
				.cooldown(CommandDefinition.Cooldown.builder()
						.enabled(true)
						.duration(2)
						.build()
				)
				.arguments(Map.of("password", "Password"))
				.hide(true)
				.build();

		CommandDefinition registerConfirm = CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("passconfirm"))
				.permission("")
				.description("Confirm credential registration")
				.usage("{alias} <password>")
				.cooldown(CommandDefinition.Cooldown.builder()
						.enabled(true)
						.duration(2)
						.build()
				)
				.arguments(Map.of("password", "Repeat"))
				.hide(true)
				.build();

		CommandDefinition login = CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("login", "l"))
				.permission("")
				.description("Login to a credential account")
				.usage("{alias} <password>")
				.cooldown(CommandDefinition.Cooldown.builder()
						.enabled(true)
						.duration(2)
						.build()
				)
				.arguments(Map.of("password", "Password"))
				.hide(true)
				.build();

		CommandDefinition changePassword = CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("changepassword", "changepass"))
				.permission("")
				.description("Change password account password")
				.usage("{alias} <current> <new> [repeat]")
				.cooldown(CommandDefinition.Cooldown.builder()
						.enabled(true)
						.duration(2)
						.build()
				)
				.arguments(Map.of("current", "Current", "new", "New", "repeat", "Repeat"))
				.build();

		CommandDefinition credential = CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("credential"))
				.permission("")
				.description("Credential account migration")
				.usage("{alias}")
				.cooldown(CommandDefinition.Cooldown.builder()
						.enabled(true)
						.duration(2)
						.build()
				)
				.build();

		CommandDefinition credentialConfirm = CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("credential confirm"))
				.permission("")
				.description("Confirm credential migration")
				.usage("{alias} [input]")
				.arguments(Map.of("input", "Code"))
				.hide(true)
				.build();

		CommandDefinition credentialCancel = CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("credential cancel"))
				.permission("")
				.description("Cancel credential migration")
				.usage("{alias}")
				.hide(true)
				.build();

		CommandDefinition adminForceRegister = CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("credential register"))
				.permission("identica.admin")
				.description("Force register a credential account")
				.usage("{command} {alias} <username> <password>")
				.cooldown(CommandDefinition.Cooldown.builder()
						.enabled(true)
						.duration(2)
						.build()
				)
				.arguments(Map.of("username", "Username", "password", "Password"))
				.build();

		CommandDefinition adminSetPassword = CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("credential setpassword"))
				.permission("identica.admin")
				.description("Set a credential account password")
				.usage("{command} {alias} <username> <password>")
				.cooldown(CommandDefinition.Cooldown.builder()
						.enabled(true)
						.duration(2)
						.build()
				)
				.arguments(Map.of("username", "Username", "password", "Password"))
				.build();

		commands.getCommands().put("pass", register);
		commands.getCommands().put("passconfirm", registerConfirm);
		commands.getCommands().put("login", login);
		commands.getCommands().put("change-password", changePassword);
		commands.getCommands().put("credential", credential);
		commands.getCommands().put("credential-confirm", credentialConfirm);
		commands.getCommands().put("credential-cancel", credentialCancel);
		commands.getCommands().put("admin-force-register", adminForceRegister);
		commands.getCommands().put("admin-set-password", adminSetPassword);

		return commands;
	}
}
