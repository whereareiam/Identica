package me.whereareiam.identica.command;

import me.whereareiam.identica.model.CommandDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

/**
 * Registers definition-driven commands and named suggestion providers.
 * <p>
 * Lifecycle owners must use distinct definition IDs and suggestion keys, and release
 * their registrations during shutdown. Registration and cleanup run serially on the
 * platform's command lifecycle thread.
 */
@SuppressWarnings("unused")
public interface CommandService {
	/**
	 * Registers commands from an instance created by dependency injection.
	 *
	 * @param key definition ID referenced by the command annotations
	 * @param definition command configuration
	 * @param commandClass class to instantiate and parse
	 */
	void registerCommand(@NotNull String key, @NotNull CommandDefinition definition, @NotNull Class<?> commandClass);

	/**
	 * Registers command classes with their definitions.
	 *
	 * @param definitions configuration keyed by definition ID
	 * @param commandClasses classes to instantiate through dependency injection
	 */
	void registerCommands(@NotNull Map<String, CommandDefinition> definitions, @NotNull Class<?>... commandClasses);

	/**
	 * Registers commands from an existing instance with its definition.
	 *
	 * @param key definition ID referenced by the command annotations
	 * @param definition command configuration
	 * @param commandInstance instance containing annotated command methods
	 */
	void registerCommandInstance(@NotNull String key, @NotNull CommandDefinition definition, @NotNull Object commandInstance);

	/**
	 * Registers annotated methods from existing instances.
	 *
	 * @param definitions configuration keyed by definition ID
	 * @param commandInstances instances to parse
	 */
	void registerCommandInstances(@NotNull Map<String, CommandDefinition> definitions, @NotNull Object... commandInstances);

	/**
	 * Removes every command chain carrying one of the supplied definition IDs,
	 * including aliases, while preserving other commands under shared roots.
	 * Unknown IDs and an empty set have no effect. Removes the corresponding
	 * runtime definitions; core configuration is not modified.
	 * <p>
	 * The platform command manager must support root deletion. Unsupported
	 * managers fail before any registrations or definitions are removed.
	 *
	 * @param definitionIds definition IDs owned by the caller
	 * @throws IllegalStateException if the platform cannot delete command roots
	 */
	void unregisterCommands(@NotNull Set<String> definitionIds);

	/**
	 * Registers or replaces a named platform suggestion provider.
	 * <p>
	 * Keys are case-insensitive. Use an owner-specific key so another lifecycle
	 * owner's provider is not replaced. The command adapter expects a Cloud
	 * suggestion provider compatible with Identica actors.
	 *
	 * @param key unique suggestion key
	 * @param suggestionProvider platform-compatible provider instance
	 */
	void registerSuggestionProvider(@NotNull String key, @NotNull Object suggestionProvider);

	/**
	 * Releases the provider registered through this service under the given key.
	 * Unknown keys and built-in providers are unaffected. Existing commands that
	 * captured this registration subsequently receive empty suggestions.
	 * <p>
	 * Cloud has no named-provider removal API, so its registry retains an empty
	 * delegate until the key is registered again. A provider installed directly
	 * into the platform registry by another owner is not replaced during cleanup.
	 *
	 * @param key case-insensitive suggestion key owned by the caller
	 */
	void unregisterSuggestionProvider(@NotNull String key);

	/**
	 * Returns the number of currently registered Cloud command chains, including
	 * separate chains generated for aliases.
	 *
	 * @return registered command count
	 */
	int getCommandCount();

	/**
	 * Returns a snapshot of registered definitions combined with core configuration.
	 * Runtime registrations take precedence over configured core definitions.
	 *
	 * @return definition map; changes to the map do not alter registrations
	 */
	@NotNull
	Map<String, CommandDefinition> getRegisteredDefinitions();
}
