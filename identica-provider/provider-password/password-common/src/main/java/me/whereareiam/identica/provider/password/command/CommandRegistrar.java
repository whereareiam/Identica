package me.whereareiam.identica.provider.password.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import me.whereareiam.identica.command.CommandService;
import me.whereareiam.identica.provider.password.config.PasswordCommands;

import java.util.Set;

@Singleton
public class CommandRegistrar {
	private final CommandService commandService;
	private final PasswordCommands passwordCommands;
	private final Set<Object> commandInstances;

	@Inject
	public CommandRegistrar(
			CommandService commandService,
			@Named("password") PasswordCommands passwordCommands,
			@Named("passwordCommandInstances") Set<Object> commandInstances
	) {
		this.commandService = commandService;
		this.passwordCommands = passwordCommands;
		this.commandInstances = commandInstances;
	}

	public void registerCommands() {
		commandService.registerCommandInstances(passwordCommands.getCommands(), commandInstances.toArray());
	}
}
