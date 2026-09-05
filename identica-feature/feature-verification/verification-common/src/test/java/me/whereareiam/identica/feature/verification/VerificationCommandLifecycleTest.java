package me.whereareiam.identica.feature.verification;

import me.whereareiam.identica.command.CommandService;
import me.whereareiam.identica.feature.verification.command.VerificationCommandRegistrar;
import me.whereareiam.identica.feature.verification.command.suggestion.VerificationMethodSuggestions;
import me.whereareiam.identica.feature.verification.config.VerificationCommands;
import me.whereareiam.identica.feature.verification.config.defaults.VerificationCommandsDefaults;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class VerificationCommandLifecycleTest {
	@Test
	void adminResetRetainsPermissionAndRegistrationsAreReleasedOnce() {
		VerificationCommands commands = new VerificationCommandsDefaults().supply(new VerificationCommands());
		assertEquals("identica.admin.2fa.reset", commands.getCommands().get("admin-verification-reset").getPermission());
		CommandService service = mock(CommandService.class);
		VerificationCommandRegistrar registrar = new VerificationCommandRegistrar(
				service, mock(VerificationMethodSuggestions.class), commands, Set.of());
		registrar.registerCommands();
		registrar.registerCommands();
		registrar.unregisterCommands();
		registrar.unregisterCommands();
		verify(service).unregisterCommands(commands.getCommands().keySet());
		verify(service).unregisterSuggestionProvider(VerificationMethodSuggestions.KEY);
	}
}
