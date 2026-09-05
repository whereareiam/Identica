package me.whereareiam.identica.feature.verification.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import me.whereareiam.identica.command.CommandService;
import me.whereareiam.identica.feature.verification.command.suggestion.VerificationMethodSuggestions;
import me.whereareiam.identica.feature.verification.config.VerificationCommands;

import java.util.Set;

@Singleton
public class VerificationCommandRegistrar {
	private final CommandService commandService;
	private final VerificationMethodSuggestions verificationMethodSuggestions;
	private final VerificationCommands verificationCommands;
	private final Set<Object> commandInstances;
	private Set<String> registeredKeys = Set.of();
	private boolean registered;

	@Inject
	public VerificationCommandRegistrar(
			CommandService commandService,
			VerificationMethodSuggestions verificationMethodSuggestions,
			VerificationCommands verificationCommands,
			@Named("verificationCommandInstances") Set<Object> commandInstances
	) {
		this.commandService = commandService;
		this.verificationMethodSuggestions = verificationMethodSuggestions;
		this.verificationCommands = verificationCommands;
		this.commandInstances = commandInstances;
	}

	public void registerCommands() {
		if (registered) return;
		registered = true;
		registeredKeys = Set.copyOf(verificationCommands.getCommands().keySet());
		commandService.registerSuggestionProvider(VerificationMethodSuggestions.KEY, verificationMethodSuggestions);
		commandService.registerCommandInstances(verificationCommands.getCommands(), commandInstances.toArray());
	}
	public void unregisterCommands() {
		if (!registered) return;
		try {
			commandService.unregisterCommands(registeredKeys);
		} finally {
			commandService.unregisterSuggestionProvider(VerificationMethodSuggestions.KEY);
			registeredKeys = Set.of();
			registered = false;
		}
	}
}
