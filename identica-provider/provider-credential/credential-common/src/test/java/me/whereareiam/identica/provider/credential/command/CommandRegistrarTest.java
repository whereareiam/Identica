package me.whereareiam.identica.provider.credential.command;

import me.whereareiam.identica.command.CommandService;
import me.whereareiam.identica.model.CommandDefinition;
import me.whereareiam.identica.provider.credential.config.CredentialCommands;
import me.whereareiam.identica.provider.credential.config.CredentialSettings;
import me.whereareiam.identica.provider.credential.config.defaults.CredentialCommandsDefaults;
import me.whereareiam.identica.provider.credential.config.defaults.CredentialSettingsDefaults;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("Credential Command Registrar")
class CommandRegistrarTest {
	private final CommandService commandService = mock(CommandService.class);
	private final CredentialCommands commands = new CredentialCommandsDefaults().supply(new CredentialCommands());

	@DisplayName("/changepassword offers the repeat argument while its repetition is required")
	@Test
	void keepsTheRepeatArgumentWhileRequired() {
		Map<String, CommandDefinition> registered = register(settings(true));

		assertEquals("{alias} <current> <new> [repeat]", registered.get("change-password").getUsage());
	}

	@DisplayName("/changepassword takes no repeat argument once its repetition is not required")
	@Test
	void dropsTheRepeatArgumentWhenNotRequired() {
		Map<String, CommandDefinition> registered = register(settings(false));

		assertEquals("{alias} <current> <new>", registered.get("change-password").getUsage());
		assertSame(commands.getCommands().get("login"), registered.get("login"));
	}

	@DisplayName("A usage an operator changed keeps everything but the repeat argument")
	@Test
	void dropsOnlyTheRepeatArgumentFromAChangedUsage() {
		commands.getCommands().put("change-password", commands.getCommands().get("change-password").toBuilder()
				.usage("{alias}  <current>   <new> <repeat>")
				.build());

		Map<String, CommandDefinition> registered = register(settings(false));

		assertEquals("{alias} <current> <new>", registered.get("change-password").getUsage());
	}

	@DisplayName("The configured definitions stay as the operator wrote them")
	@Test
	void leavesTheConfiguredDefinitionUnchanged() {
		register(settings(false));

		assertEquals("{alias} <current> <new> [repeat]", commands.getCommands().get("change-password").getUsage());
	}

	@SuppressWarnings("unchecked")
	private Map<String, CommandDefinition> register(CredentialSettings settings) {
		new CommandRegistrar(commandService, commands, () -> settings, Set.of()).registerCommands();

		ArgumentCaptor<Map<String, CommandDefinition>> definitions = ArgumentCaptor.forClass(Map.class);
		verify(commandService).registerCommandInstances(definitions.capture(), any(Object[].class));
		return definitions.getValue();
	}

	private static CredentialSettings settings(boolean requireRepeat) {
		CredentialSettings settings = new CredentialSettingsDefaults().supply(new CredentialSettings());
		settings.getScenario().getChangePassword().setRequireRepeat(requireRepeat);
		return settings;
	}
}
