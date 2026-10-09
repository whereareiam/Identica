package me.whereareiam.identica.platform.bungeecord;

import io.leangen.geantyref.GenericTypeReflector;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.TabExecutor;
import org.incendo.cloud.CloudCapability;
import org.incendo.cloud.Command;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.bungee.BungeeCommandManager;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandNode;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.permission.Permission;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.util.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;

/**
 * Keeps Bungee command wrappers in sync with Cloud root registration and deletion.
 */
final class LifecycleBungeeCommandManager<C> extends BungeeCommandManager<C> {
	LifecycleBungeeCommandManager(
			@NotNull Plugin plugin,
			@NotNull ExecutionCoordinator<C> coordinator,
			@NotNull SenderMapper<CommandSender, C> mapper
	) {
		super(plugin, coordinator, mapper);
		commandRegistrationHandler(new RegistrationHandler());
		registerCapability(CloudCapability.StandardCapabilities.ROOT_COMMAND_DELETION);
	}

	private final class RegistrationHandler implements CommandRegistrationHandler<C> {
		private final Map<String, PlatformCommand> registrations = new HashMap<>();

		@Override
		public boolean registerCommand(@NotNull Command<C> command) {
			CommandNode<C> root = Objects.requireNonNull(commandTree().getNamedNode(command.rootComponent().name()));
			CommandComponent<C> component = Objects.requireNonNull(root.component());
			PlatformCommand previous = registrations.get(component.name());
			if (previous != null && new HashSet<>(Arrays.asList(previous.getAliases()))
					.equals(new HashSet<>(component.alternativeAliases()))) return false;

			var pluginManager = owningPlugin().getProxy().getPluginManager();
			if (previous != null) pluginManager.unregisterCommand(previous);
			PlatformCommand replacement = new PlatformCommand(component);
			pluginManager.registerCommand(owningPlugin(), replacement);
			registrations.put(component.name(), replacement);
			return true;
		}

		@Override
		public void unregisterRootCommand(@NotNull CommandComponent<C> rootCommand) {
			PlatformCommand registered = registrations.get(rootCommand.name());
			if (registered == null) return;
			owningPlugin().getProxy().getPluginManager().unregisterCommand(registered);
			registrations.remove(rootCommand.name());
		}
	}

	private final class PlatformCommand extends net.md_5.bungee.api.plugin.Command implements TabExecutor {
		private PlatformCommand(@NotNull CommandComponent<C> root) {
			super(root.name(), "", root.alternativeAliases().toArray(String[]::new));
		}

		@Override
		public void execute(@NotNull CommandSender sender, @NotNull String[] arguments) {
			commandExecutor().executeCommand(senderMapper().map(sender), input(arguments));
		}

		@Override
		public boolean hasPermission(@NotNull CommandSender sender) {
			CommandNode<C> root = commandTree().getNamedNode(getName());
			if (root == null) return false;

			C mapped = senderMapper().map(sender);
			Map<Type, Permission> permissions = root.nodeMeta().getOrDefault(CommandNode.META_KEY_ACCESS, Map.of());
			return permissions.entrySet().stream().anyMatch(entry ->
					GenericTypeReflector.isSuperType(entry.getKey(), mapped.getClass())
							&& testPermission(mapped, entry.getValue()).allowed());
		}

		@Override
		public @NotNull Iterable<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] arguments) {
			var suggestions = suggestionFactory().suggestImmediately(senderMapper().map(sender), input(arguments));
			return suggestions.list().stream()
					.map(Suggestion::suggestion)
					.map(value -> StringUtils.trimBeforeLastSpace(value, suggestions.commandInput()))
					.filter(Objects::nonNull)
					.toList();
		}

		private @NotNull String input(@NotNull String[] arguments) {
			return arguments.length == 0 ? getName() : getName() + " " + String.join(" ", arguments);
		}
	}
}
