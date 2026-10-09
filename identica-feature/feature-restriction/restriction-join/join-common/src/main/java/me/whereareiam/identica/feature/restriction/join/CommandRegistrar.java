package me.whereareiam.identica.feature.restriction.join;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import me.whereareiam.identica.command.CommandService;
import me.whereareiam.identica.feature.restriction.join.config.JoinRestrictionCommands;

import java.util.Set;

@Singleton
public class CommandRegistrar {
	private final CommandService commandService;
	private final JoinRestrictionCommands commands;
	private final Set<Object> commandInstances;
	private Set<String> registeredKeys = Set.of();

	@Inject
	public CommandRegistrar(
			CommandService commandService,
			@Named("joinRestriction") JoinRestrictionCommands commands,
			@Named("joinRestrictionCommandInstances") Set<Object> commandInstances
	) {
		this.commandService = commandService;
		this.commands = commands;
		this.commandInstances = commandInstances;
	}

	public void unregisterCommands() {
		commandService.unregisterCommands(registeredKeys);
		registeredKeys = Set.of();
	}

	public void registerCommands() {
		registeredKeys = Set.copyOf(commands.getCommands().keySet());
		commandService.registerCommandInstances(commands.getCommands(), commandInstances.toArray());
	}
}
