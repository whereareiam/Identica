package me.whereareiam.identica.provider.password.config.defaults;

import com.google.inject.Singleton;
import me.whereareiam.configura.merge.defaults.MergeDefaultsProvider;
import me.whereareiam.identica.model.CommandDefinition;
import me.whereareiam.identica.provider.password.config.PasswordCommands;

import java.util.List;
import java.util.Map;

@Singleton
public class PasswordCommandsDefaults implements MergeDefaultsProvider<PasswordCommands> {
	@Override
	public PasswordCommands supply(PasswordCommands commands) {
		CommandDefinition register = CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("pass"))
				.permission("")
				.description("Register a password account")
				.usage("{alias} <password>")
				.cooldown(CommandDefinition.Cooldown.builder()
						.enabled(true)
						.duration(2)
						.group("global")
						.build()
				)
				.arguments(Map.of("password", "Password"))
				.hide(true)
				.build();

		CommandDefinition registerConfirm = CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("passconfirm"))
				.permission("")
				.description("Confirm password registration")
				.usage("{alias} <password>")
				.cooldown(CommandDefinition.Cooldown.builder()
						.enabled(true)
						.duration(2)
						.group("global")
						.build()
				)
				.arguments(Map.of("password", "Repeat"))
				.hide(true)
				.build();

		CommandDefinition login = CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("login", "l"))
				.permission("")
				.description("Login to a password account")
				.usage("{alias} <password>")
				.cooldown(CommandDefinition.Cooldown.builder()
						.enabled(true)
						.duration(2)
						.group("global")
						.build()
				)
				.arguments(Map.of("password", "Password"))
				.hide(true)
				.build();

		CommandDefinition changePassword = CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("changepassword", "changepass", "password"))
				.permission("")
				.description("Change password account password")
				.usage("{alias} <current> <new> [repeat]")
				.cooldown(CommandDefinition.Cooldown.builder()
						.enabled(true)
						.duration(2)
						.group("global")
						.build()
				)
				.arguments(Map.of("current", "Current", "new", "New", "repeat", "Repeat"))
				.build();

		CommandDefinition password = CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("password"))
				.permission("")
				.description("Password account migration")
				.usage("{alias}")
				.cooldown(CommandDefinition.Cooldown.builder()
						.enabled(true)
						.duration(2)
						.group("global")
						.build()
				)
				.build();

		CommandDefinition passwordConfirm = CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("password confirm"))
				.permission("")
				.description("Confirm password migration")
				.usage("{alias} [input]")
				.arguments(Map.of("input", "Code"))
				.hide(true)
				.build();

		CommandDefinition passwordCancel = CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("password cancel"))
				.permission("")
				.description("Cancel password migration")
				.usage("{alias}")
				.hide(true)
				.build();

		CommandDefinition adminForceRegister = CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("password register"))
				.permission("identica.admin")
				.description("Force register a password account")
				.usage("{command} {alias} <username> <password>")
				.cooldown(CommandDefinition.Cooldown.builder()
						.enabled(true)
						.duration(2)
						.group("global")
						.build()
				)
				.arguments(Map.of("username", "Username", "password", "Password"))
				.build();

		CommandDefinition adminSetPassword = CommandDefinition.builder()
				.enabled(true)
				.aliases(List.of("password setpassword"))
				.permission("identica.admin")
				.description("Set a password account password")
				.usage("{command} {alias} <username> <password>")
				.cooldown(CommandDefinition.Cooldown.builder()
						.enabled(true)
						.duration(2)
						.group("global")
						.build()
				)
				.arguments(Map.of("username", "Username", "password", "Password"))
				.build();

		commands.getCommands().put("pass", register);
		commands.getCommands().put("passconfirm", registerConfirm);
		commands.getCommands().put("login", login);
		commands.getCommands().put("change-password", changePassword);
		commands.getCommands().put("password", password);
		commands.getCommands().put("password-confirm", passwordConfirm);
		commands.getCommands().put("password-cancel", passwordCancel);
		commands.getCommands().put("admin-force-register", adminForceRegister);
		commands.getCommands().put("admin-set-password", adminSetPassword);

		return commands;
	}
}
