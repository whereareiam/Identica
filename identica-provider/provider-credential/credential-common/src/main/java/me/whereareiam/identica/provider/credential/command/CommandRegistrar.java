package me.whereareiam.identica.provider.credential.command;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import me.whereareiam.identica.command.CommandService;
import me.whereareiam.identica.model.CommandDefinition;
import me.whereareiam.identica.provider.credential.config.CredentialCommands;
import me.whereareiam.identica.provider.credential.config.CredentialSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class CommandRegistrar {
	private final CommandService commandService;
	private final CredentialCommands credentialCommands;
	private final Provider<CredentialSettings> settingsProvider;
	private final Set<Object> commandInstances;

	@Inject
	public CommandRegistrar(
			CommandService commandService,
			@Named("credential") CredentialCommands credentialCommands,
			Provider<CredentialSettings> settingsProvider,
			@Named("credentialCommandInstances") Set<Object> commandInstances
	) {
		this.commandService = commandService;
		this.credentialCommands = credentialCommands;
		this.settingsProvider = settingsProvider;
		this.commandInstances = commandInstances;
	}

	public void registerCommands() {
		commandService.registerCommandInstances(definitions(), commandInstances.toArray());
	}

	/**
	 * Returns the configured definitions as they are registered: {@code /changepassword} takes no repeat argument
	 * while its repetition is not required, so neither its syntax nor its usage offers one.
	 */
	private @NotNull Map<String, CommandDefinition> definitions() {
		Map<String, CommandDefinition> definitions = new LinkedHashMap<>(credentialCommands.getCommands());
		if (requiresChangeRepeat()) return definitions;

		CommandDefinition changePassword = definitions.get(PasswordChangeCommand.DEFINITION);
		if (changePassword != null)
			definitions.put(PasswordChangeCommand.DEFINITION, changePassword.toBuilder()
					.usage(withoutArgument(changePassword.getUsage(), PasswordChangeCommand.REPEAT_ARGUMENT))
					.build());

		return definitions;
	}

	private boolean requiresChangeRepeat() {
		CredentialSettings settings = settingsProvider.get();
		if (settings == null || settings.getScenario() == null) return true;

		CredentialSettings.Scenario.ChangePassword changePassword = settings.getScenario().getChangePassword();
		return changePassword == null || changePassword.isRequireRepeat();
	}

	private static @Nullable String withoutArgument(@Nullable String usage, @NotNull String argument) {
		if (usage == null) return null;

		return Arrays.stream(usage.trim().split("\\s+"))
				.filter(token -> !argument.equals(argumentName(token)))
				.collect(Collectors.joining(" "));
	}

	private static @Nullable String argumentName(@NotNull String token) {
		boolean required = token.startsWith("<") && token.endsWith(">");
		boolean optional = token.startsWith("[") && token.endsWith("]");
		if (!required && !optional) return null;

		String name = token.substring(1, token.length() - 1).trim();
		return name.endsWith("...") ? name.substring(0, name.length() - 3) : name;
	}
}
