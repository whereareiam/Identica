package me.whereareiam.identica.adapter.command;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.commandant.Commandant;
import me.whereareiam.commandant.CommandantKeys;
import me.whereareiam.commandant.CommandantSyntaxFormatter;
import me.whereareiam.commandant.exception.ExceptionHandlerRegistrar;
import me.whereareiam.commandant.exception.format.CloudPermissionFormatters;
import me.whereareiam.commandant.exception.format.ExceptionFormatting;
import me.whereareiam.commandant.model.message.ExceptionMessages;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.adapter.command.annotation.IdenticaAnnotationParser;
import me.whereareiam.identica.adapter.command.definition.CommandDefinitionAdapter;
import me.whereareiam.identica.adapter.command.executor.*;
import me.whereareiam.identica.adapter.command.executor.admin.*;
import me.whereareiam.identica.adapter.command.parser.PasswordParser;
import me.whereareiam.identica.adapter.command.serializer.ScopedSerializerEngine;
import me.whereareiam.identica.adapter.command.suggestion.CrossPlayerSuggestions;
import me.whereareiam.identica.adapter.command.suggestion.ProviderIdSuggestions;
import me.whereareiam.identica.command.CommandService;
import me.whereareiam.identica.model.CommandDefinition;
import me.whereareiam.identica.model.config.Commands;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.keystone.Actor;
import me.whereareiam.keystone.serializer.SerializerEngine;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.CloudCapability;
import org.incendo.cloud.setting.ManagerSetting;
import org.incendo.cloud.internal.CommandNode;
import org.incendo.cloud.permission.Permission;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Singleton
public class DefaultCommandService implements CommandService {
	private final Provider<Commands> commandsProvider;
	private final Provider<Messages> messagesProvider;
	private final Provider<CommandManager<Actor>> commandManagerProvider;
	private final SerializerEngine serializer;
	private final Injector injector;
	private final CrossPlayerSuggestions crossPlayerSuggestions;
	private final ProviderIdSuggestions providerIdSuggestions;

	private final Map<String, CommandDefinition> registeredDefinitions = new HashMap<>();
	private final Map<String, AtomicReference<SuggestionProvider<Actor>>> suggestionProviders = new HashMap<>();
	private IdenticaAnnotationParser<Actor> annotationParser;

	@Inject
	public DefaultCommandService(
			Provider<Commands> commandsProvider,
			Provider<Messages> messagesProvider,
			Provider<CommandManager<Actor>> commandManagerProvider,
			SerializerEngine serializer,
			Injector injector,
			CrossPlayerSuggestions crossPlayerSuggestions,
			ProviderIdSuggestions providerIdSuggestions
	) {
		this.commandsProvider = commandsProvider;
		this.messagesProvider = messagesProvider;
		this.commandManagerProvider = commandManagerProvider;
		this.serializer = serializer;
		this.injector = injector;
		this.crossPlayerSuggestions = crossPlayerSuggestions;
		this.providerIdSuggestions = providerIdSuggestions;

		initialize();
	}

	private void initialize() {
		CommandManager<Actor> commandManager = commandManagerProvider.get();
		registerSyntaxFormatter(commandManager);
		registerSuggestions(commandManager);
		registerInternal(
				injector.getInstance(PasswordParser.class),
				injector.getInstance(MainCommand.class),
				injector.getInstance(HelpCommand.class),
				injector.getInstance(ReloadCommand.class),
				injector.getInstance(AdminRootCommand.class),
				injector.getInstance(ClearCommand.class),
				injector.getInstance(DeleteCommand.class),
				injector.getInstance(ReservationCommand.class),
				injector.getInstance(SessionsCommand.class),
				injector.getInstance(EnrollCommand.class),
				injector.getInstance(MigrationCommand.class),
				injector.getInstance(AvailabilityCommand.class)
		);

		registerExceptionHandlers(commandManager);
	}

	@Override
	public void registerCommand(@NotNull String key, @NotNull CommandDefinition definition, @NotNull Class<?> commandClass) {
		Object instance = injector.getInstance(commandClass);
		registerCommandInstance(key, definition, instance);
	}

	@Override
	public void registerCommands(@NotNull Map<String, CommandDefinition> definitions, @NotNull Class<?>... commandClasses) {
		Object[] instances = new Object[commandClasses.length];
		for (int i = 0; i < commandClasses.length; i++)
			instances[i] = injector.getInstance(commandClasses[i]);

		registerCommandInstances(definitions, instances);
	}

	@Override
	public void registerCommandInstance(@NotNull String key, @NotNull CommandDefinition definition, @NotNull Object commandInstance) {
		registeredDefinitions.put(key, definition);
		registerInternal(commandInstance);
	}

	@Override
	public void registerCommandInstances(@NotNull Map<String, CommandDefinition> definitions, @NotNull Object... commandInstances) {
		registeredDefinitions.putAll(definitions);
		registerInternal(commandInstances);
	}

	@Override
	public void unregisterCommands(@NotNull Set<String> definitionIds) {
		if (definitionIds.isEmpty()) return;

		CommandManager<Actor> manager = commandManagerProvider.get();
		List<Command<Actor>> commands = List.copyOf(manager.commands());
		Set<CommandNode<Actor>> affectedRoots = new LinkedHashSet<>();
		for (Command<Actor> command : commands) {
			if (!ownsCommand(command, definitionIds)) continue;
			affectedRoots.add(Objects.requireNonNull(
					manager.commandTree().getNamedNode(command.rootComponent().name())
			));
		}

		if (!affectedRoots.isEmpty()) {
			if (!manager.hasCapability(CloudCapability.StandardCapabilities.ROOT_COMMAND_DELETION))
				throw new IllegalStateException("The platform command manager does not support root command deletion");

			List<Command<Actor>> retained = commands.stream()
					.filter(command -> !ownsCommand(command, definitionIds))
					.filter(command -> affectedRoots.contains(
							manager.commandTree().getNamedNode(command.rootComponent().name())))
					.toList();

			// Cloud 2.0.0 deletes entire roots. Reinsert the existing command objects
			// so handlers, definition IDs, permissions and aliases survive unchanged.
			boolean unsafeRegistration = manager.settings().get(ManagerSetting.ALLOW_UNSAFE_REGISTRATION);
			try {
				manager.settings().set(ManagerSetting.ALLOW_UNSAFE_REGISTRATION, true);
				for (CommandNode<Actor> root : affectedRoots)
					manager.deleteRootCommand(Objects.requireNonNull(root.component()).name());
				retained.forEach(manager::command);
			} finally {
				manager.settings().set(ManagerSetting.ALLOW_UNSAFE_REGISTRATION, unsafeRegistration);
			}
		}

		definitionIds.forEach(registeredDefinitions::remove);
	}

	private boolean ownsCommand(@NotNull Command<Actor> command, @NotNull Set<String> definitionIds) {
		return command.commandMeta().optional(CommandantKeys.DEFINITION_ID)
				.map(definitionIds::contains).orElse(false);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void registerSuggestionProvider(@NotNull String key, @NotNull Object suggestionProvider) {
		SuggestionProvider<Actor> provider = (SuggestionProvider<Actor>) suggestionProvider;
		String normalizedKey = key.toLowerCase(Locale.ENGLISH);
		AtomicReference<SuggestionProvider<Actor>> delegate = suggestionProviders.computeIfAbsent(
				normalizedKey, ignored -> new AtomicReference<>(provider)
		);
		delegate.set(provider);
		commandManagerProvider.get().parserRegistry().registerSuggestionProvider(
				key, (context, input) -> delegate.get().suggestionsFuture(context, input)
		);
	}

	@Override
	public void unregisterSuggestionProvider(@NotNull String key) {
		AtomicReference<SuggestionProvider<Actor>> delegate = suggestionProviders.remove(key.toLowerCase(Locale.ENGLISH));
		if (delegate != null)
			delegate.set(SuggestionProvider.noSuggestions());
	}

	@Override
	public int getCommandCount() {
		return commandManagerProvider.get().commands().size();
	}

	@Override
	public @NotNull Map<String, CommandDefinition> getRegisteredDefinitions() {
		Map<String, CommandDefinition> all = new HashMap<>(registeredDefinitions);
		Commands commands = commandsProvider.get();
		if (commands != null) {
			commands.getCommands()
					.forEach(all::putIfAbsent);
		}

		return all;
	}

	private void registerInternal(Object... commandInstances) {
		CommandManager<Actor> commandManager = commandManagerProvider.get();
		if (annotationParser == null) {
			annotationParser = IdenticaAnnotationParser.create(commandManager, Actor.class);
		}

		Collection<Command<Actor>> parsed = annotationParser.parse(commandInstances);
		processParsedCommands(parsed, commandManager);
	}

	private void registerSuggestions(@NotNull CommandManager<Actor> commandManager) {
		commandManager.parserRegistry()
				.registerSuggestionProvider(CrossPlayerSuggestions.KEY, crossPlayerSuggestions);
		commandManager.parserRegistry()
				.registerSuggestionProvider(ProviderIdSuggestions.KEY, providerIdSuggestions);
	}

	private void registerSyntaxFormatter(@NotNull CommandManager<Actor> commandManager) {
		Messages messages = messagesProvider.get();
		commandManager.commandSyntaxFormatter(new CommandantSyntaxFormatter<>(
				commandManager,
				collectArgumentDescriptions(),
				messages.getCommands().getHelp().getArgumentFormat(),
				Serializer.getEngine().getPlaceholderFormat()
		));
	}

	private @NotNull Map<String, String> collectArgumentDescriptions() {
		Map<String, String> argumentDescriptions = new HashMap<>();
		Commands commands = commandsProvider.get();
		if (commands == null) return argumentDescriptions;

		for (CommandDefinition definition : commands.getCommands().values()) {
			Map<String, String> arguments = definition.getArguments();
			if (arguments == null || arguments.isEmpty()) continue;
			argumentDescriptions.putAll(arguments);
		}

		return argumentDescriptions;
	}

	private void processParsedCommands(
			@NotNull Collection<Command<Actor>> parsed,
			@NotNull CommandManager<Actor> commandManager
	) {
		CommandDefinitionAdapter adapter = new CommandDefinitionAdapter();
		List<String> rootAliases = resolveRootAliases(lookupDefinition("main"));

		for (Command<Actor> command : parsed) {
			String defId = command.commandMeta().optional(CommandantKeys.DEFINITION_ID).orElse(null);
			CommandDefinition definition = defId != null ? lookupDefinition(defId) : null;
			boolean sharedRootCommand = definition != null
					&& ("main".equals(defId) || isSubcommand(definition));

			Commandant.process(command, commandManager)
					.withDefinition(definition, adapter, sharedRootCommand ? rootAliases : List.of())
					.register();
		}
	}

	private boolean isSubcommand(@NotNull CommandDefinition definition) {
		String usage = definition.getUsage();
		return usage != null && usage.contains("{command}");
	}

	private @NotNull List<String> resolveRootAliases(@Nullable CommandDefinition definition) {
		if (definition == null || definition.getAliases() == null || definition.getAliases().isEmpty())
			return List.of();

		LinkedHashSet<String> resolved = new LinkedHashSet<>();
		for (String alias : definition.getAliases()) {
			if (alias == null) continue;
			String trimmed = alias.trim();
			if (!trimmed.isEmpty()) resolved.add(trimmed);
		}

		return List.copyOf(resolved);
	}

	private CommandDefinition lookupDefinition(@NotNull String key) {
		CommandDefinition registered = registeredDefinitions.get(key);
		if (registered != null) return registered;
		Commands commands = commandsProvider.get();

		return commands.getCommands().get(key);
	}

	private void registerExceptionHandlers(@NotNull CommandManager<Actor> commandManager) {
		ExceptionMessages exceptionMessages = messagesProvider.get().getCommands().getExceptions();

		SerializerEngine scopedSerializer = new ScopedSerializerEngine(serializer, Serializer.SCOPE);
		ExceptionHandlerRegistrar.register(
				commandManager,
				exceptionMessages,
				scopedSerializer,
				Actor::getAudience,
				ExceptionFormatting.builder()
						.format(Permission.class, CloudPermissionFormatters.minimal())
						.build()
		);
	}
}
