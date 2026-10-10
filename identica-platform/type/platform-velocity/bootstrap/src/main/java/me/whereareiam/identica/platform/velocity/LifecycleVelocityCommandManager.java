package me.whereareiam.identica.platform.velocity;

import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import org.incendo.cloud.CloudCapability;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandNode;
import org.incendo.cloud.velocity.VelocityCommandManager;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Adds lifecycle deletion while retaining Cloud's RAW and Brigadier registration paths.
 */
final class LifecycleVelocityCommandManager<C> extends VelocityCommandManager<C> {
	private final com.velocitypowered.api.command.CommandManager platformCommands;
	private final Map<String, Set<CommandMeta>> registrations = new HashMap<>();

	LifecycleVelocityCommandManager(
			@NotNull PluginContainer plugin,
			@NotNull ProxyServer proxy,
			@NotNull ExecutionCoordinator<C> coordinator,
			@NotNull SenderMapper<CommandSource, C> mapper,
			@NotNull RegistrationMode mode
	) {
		super(plugin, proxy, coordinator, mapper, mode);
		platformCommands = proxy.getCommandManager();
		registerCapability(CloudCapability.StandardCapabilities.ROOT_COMMAND_DELETION);
	}

	@Override
	public @NotNull CommandManager<C> command(@NotNull Command<? extends C> command) {
		Set<String> aliasesBefore = new HashSet<>(command.rootComponent().aliases());
		for (CommandNode<C> node : commandTree().rootNodes())
			aliasesBefore.addAll(Objects.requireNonNull(node.component()).aliases());
		Map<String, CommandMeta> metadataBefore = new HashMap<>();
		for (String alias : aliasesBefore) {
			String normalized = alias.toLowerCase(Locale.ENGLISH);
			metadataBefore.put(normalized, platformCommands.getCommandMeta(normalized));
		}

		super.command(command);
		// Cloud revisits every leaf on insertion, potentially replacing the platform
		// registration for every root. Capture the resulting metadata for all roots.
		for (CommandNode<C> node : commandTree().rootNodes()) {
			var component = Objects.requireNonNull(node.component());
			Set<CommandMeta> previous = registrations.getOrDefault(component.name(), Set.of());
			Set<CommandMeta> metadata = new HashSet<>();
			for (String alias : component.aliases()) {
				String normalized = alias.toLowerCase(Locale.ENGLISH);
				CommandMeta meta = platformCommands.getCommandMeta(normalized);
				// An unchanged lookup can belong to another plugin. Claim only new
				// registrations or metadata already known to be ours.
				if (meta != null && (previous.contains(meta) || meta != metadataBefore.get(normalized)))
					metadata.add(meta);
			}
			registrations.put(component.name(), metadata);
		}
		return this;
	}

	@Override
	public void deleteRootCommand(@NotNull String rootCommand) {
		CommandNode<C> node = commandTree().getNamedNode(rootCommand);
		if (node == null) return;

		String root = Objects.requireNonNull(node.component()).name();
		Set<CommandMeta> metadata = registrations.getOrDefault(root, Set.of());
		// Velocity's metadata overload removes only aliases still owned by that
		// registration, including secondary aliases and their Brigadier children.
		metadata.forEach(platformCommands::unregister);
		super.deleteRootCommand(root);
		registrations.remove(root);
	}
}
