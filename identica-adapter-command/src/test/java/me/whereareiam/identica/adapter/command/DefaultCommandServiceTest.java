package me.whereareiam.identica.adapter.command;

import com.google.inject.Injector;
import me.whereareiam.commandant.CommandantKeys;
import me.whereareiam.commandant.model.message.ExceptionMessages;
import me.whereareiam.commandant.model.message.HelpMessages;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.adapter.command.suggestion.CrossPlayerSuggestions;
import me.whereareiam.identica.adapter.command.suggestion.ProviderIdSuggestions;
import me.whereareiam.identica.annotation.Command;
import me.whereareiam.identica.annotation.Definition;
import me.whereareiam.identica.model.CommandDefinition;
import me.whereareiam.identica.model.config.Commands;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.keystone.Actor;
import me.whereareiam.keystone.model.SerializerOptions;
import me.whereareiam.keystone.serializer.SerializerEngine;
import org.incendo.cloud.CloudCapability;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.setting.ManagerSetting;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DefaultCommandServiceTest {
	private TestCommandManager manager;
	private DefaultCommandService service;
	private Actor actor;
	private CommandRegistrationHandler<Actor> registrationHandler;
	private CrossPlayerSuggestions coreSuggestions;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		registrationHandler = mock(CommandRegistrationHandler.class);
		manager = new TestCommandManager(registrationHandler);
		actor = mock(Actor.class);
		coreSuggestions = mock(CrossPlayerSuggestions.class);
		service = createService();
	}

	@SuppressWarnings("unchecked")
	private DefaultCommandService createService() {
		Commands commands = new Commands();
		commands.getCommands().put("main", definition("", "identica", "id"));
		Messages messages = new Messages();
		Messages.Commands commandMessages = new Messages.Commands();
		commandMessages.setHelp(new HelpMessages());
		commandMessages.setExceptions(new ExceptionMessages());
		messages.setCommands(commandMessages);
		SerializerEngine serializer = mock(SerializerEngine.class);
		when(serializer.getPlaceholderFormat()).thenReturn(SerializerOptions.PlaceholderFormat.CURLY_BRACES);
		Serializer.initialize(() -> serializer);
		Injector injector = mock(Injector.class);
		when(injector.getInstance(any(Class.class))).thenAnswer(invocation -> mock((Class<?>) invocation.getArgument(0)));
		return new DefaultCommandService(
				() -> commands,
				() -> messages,
				() -> manager,
				serializer,
				injector,
				coreSuggestions,
				mock(ProviderIdSuggestions.class)
		);
	}

	@Test
	void removesOnlyFeatureBranchesAndRetainsCommandsRegisteredLater() {
		service.registerCommandInstance("feature", definition("{command}", "verify", "verification"), new FeatureCommand());
		service.registerCommandInstance("provider", definition("{command}", "login"), new ProviderCommand());
		manager.command(manager.commandBuilder("identica", "id").literal("external").handler(context -> {}));
		List<org.incendo.cloud.Command<Actor>> retained = manager.commands().stream()
				.filter(command -> !command.commandMeta().optional(CommandantKeys.DEFINITION_ID).orElse("").equals("feature"))
				.toList();
		manager.lock();

		service.unregisterCommands(Set.of("feature"));

		assertEquals(retained, List.copyOf(manager.commands()));
		assertEquals(retained.size(), service.getCommandCount());
		assertFalse(service.getRegisteredDefinitions().containsKey("feature"));
		assertTrue(service.getRegisteredDefinitions().containsKey("provider"));
		assertTrue(service.getRegisteredDefinitions().containsKey("main"));
		assertNotNull(parse("identica"));
		assertNotNull(parse("id login"));
		assertNotNull(parse("identica external"));
		assertThrows(RuntimeException.class, () -> parse("identica verify"));
		assertThrows(RuntimeException.class, () -> parse("id verification"));
		assertFalse(manager.settings().get(ManagerSetting.ALLOW_UNSAFE_REGISTRATION));
	}

	@Test
	void cleansFeatureRootsAndAliasesAndAllowsRegistrationAgain() {
		CommandDefinition definition = definition("", "verify", "v", "check");
		service.registerCommandInstance("feature", definition, new FeatureCommand());
		assertNotNull(parse("v"));
		assertNotNull(parse("check"));

		service.unregisterCommands(Set.of("feature"));
		service.unregisterCommands(Set.of("feature", "unknown"));

		for (String alias : List.of("verify", "v", "check")) {
			assertNull(manager.commandTree().getNamedNode(alias));
			assertThrows(RuntimeException.class, () -> parse(alias));
		}
		verify(registrationHandler, atLeastOnce()).unregisterRootCommand(any());
		assertNotNull(parse("identica"));
		service.registerCommandInstance("feature", definition, new FeatureCommand());
		assertNotNull(parse("v"));
	}

	@Test
	void retainsProviderBranchUnderFeatureRootButDropsFeatureOnlyAliases() {
		service.registerCommandInstance("feature", definition("", "verify", "v"), new FeatureCommand());
		service.registerCommandInstance("provider", definition("", "verify login"), new ProviderCommand());

		service.unregisterCommands(Set.of("feature"));

		assertNotNull(parse("verify login"));
		assertNull(manager.commandTree().getNamedNode("v"));
		assertThrows(RuntimeException.class, () -> parse("verify"));
		assertTrue(service.getRegisteredDefinitions().containsKey("provider"));
	}

	@Test
	void supportsProviderShutdownBeforeFeatureShutdown() {
		service.registerCommandInstance("feature", definition("{command}", "verify"), new FeatureCommand());
		service.registerCommandInstance("provider", definition("{command}", "login"), new ProviderCommand());
		service.unregisterCommands(Set.of("provider"));
		assertNotNull(parse("id verify"));
		assertThrows(RuntimeException.class, () -> parse("id login"));

		service.unregisterCommands(Set.of("feature"));

		assertNotNull(parse("id"));
		assertNotNull(parse("identica admin"));
		assertThrows(RuntimeException.class, () -> parse("id verify"));
	}

	@Test
	void unknownDefinitionsDoNotRebuildAnyRoots() {
		List<org.incendo.cloud.Command<Actor>> commands = List.copyOf(manager.commands());
		clearInvocations(registrationHandler);

		service.unregisterCommands(Set.of());
		service.unregisterCommands(Set.of("unknown"));

		assertEquals(commands, List.copyOf(manager.commands()));
		verifyNoInteractions(registrationHandler);
	}

	@Test
	void unsupportedDeletionFailsBeforeMutatingCommandsOrDefinitions() {
		service.registerCommandInstance("feature", definition("{command}", "verify"), new FeatureCommand());
		manager.deletionSupported = false;
		List<org.incendo.cloud.Command<Actor>> commands = List.copyOf(manager.commands());

		assertThrows(IllegalStateException.class, () -> service.unregisterCommands(Set.of("feature")));

		assertEquals(commands, List.copyOf(manager.commands()));
		assertTrue(service.getRegisteredDefinitions().containsKey("feature"));
		assertNotNull(parse("id verify"));
	}

	@Test
	void suggestionCleanupDisablesCapturedDelegateAndPreservesOtherOwners() {
		service.registerSuggestionProvider("feature:values", SuggestionProvider.suggestingStrings("feature"));
		SuggestionProvider<Actor> captured = manager.parserRegistry().getSuggestionProvider("feature:values").orElseThrow();
		service.registerSuggestionProvider("provider:values", SuggestionProvider.suggestingStrings("provider"));
		assertEquals(List.of("feature"), suggestions(captured));

		service.unregisterSuggestionProvider("FEATURE:VALUES");
		service.unregisterSuggestionProvider(CrossPlayerSuggestions.KEY);
		service.unregisterSuggestionProvider("unknown");

		assertEquals(List.of(), suggestions(captured));
		assertSame(captured, manager.parserRegistry().getSuggestionProvider("feature:values").orElseThrow());
		assertEquals(List.of("provider"), suggestions(manager.parserRegistry().getSuggestionProvider("provider:values").orElseThrow()));
		assertSame(coreSuggestions, manager.parserRegistry().getSuggestionProvider(CrossPlayerSuggestions.KEY).orElseThrow());
		assertTrue(manager.parserRegistry().getSuggestionProvider("unknown").isEmpty());
		service.registerSuggestionProvider("feature:values", SuggestionProvider.suggestingStrings("reloaded"));
		assertEquals(List.of("reloaded"), suggestions(manager.parserRegistry().getSuggestionProvider("feature:values").orElseThrow()));
		assertEquals(List.of(), suggestions(captured));
	}

	@Test
	void replacingSuggestionRegistrationUpdatesCapturedCommands() {
		service.registerSuggestionProvider("feature:values", SuggestionProvider.suggestingStrings("first"));
		SuggestionProvider<Actor> captured = manager.parserRegistry().getSuggestionProvider("feature:values").orElseThrow();
		service.registerSuggestionProvider("FEATURE:VALUES", SuggestionProvider.suggestingStrings("second"));
		assertEquals(List.of("second"), suggestions(captured));

		service.unregisterSuggestionProvider("feature:values");

		assertEquals(List.of(), suggestions(captured));
		assertEquals(List.of(), suggestions(manager.parserRegistry().getSuggestionProvider("feature:values").orElseThrow()));
	}

	@Test
	void cleanupDoesNotOverwriteAProviderInstalledDirectlyByAnotherOwner() {
		service.registerSuggestionProvider("feature:values", SuggestionProvider.suggestingStrings("feature"));
		SuggestionProvider<Actor> captured = manager.parserRegistry().getSuggestionProvider("feature:values").orElseThrow();
		SuggestionProvider<Actor> replacement = SuggestionProvider.suggestingStrings("external");
		manager.parserRegistry().registerSuggestionProvider("feature:values", replacement);

		service.unregisterSuggestionProvider("feature:values");

		assertSame(replacement, manager.parserRegistry().getSuggestionProvider("feature:values").orElseThrow());
		assertEquals(List.of(), suggestions(captured));
		assertEquals(List.of("external"), suggestions(replacement));
	}

	private org.incendo.cloud.Command<Actor> parse(String input) {
		return manager.commandTree().parse(new CommandContext<>(actor, manager), CommandInput.of(input), Runnable::run).join();
	}

	private List<String> suggestions(SuggestionProvider<Actor> provider) {
		List<String> values = new ArrayList<>();
		provider.suggestionsFuture(new CommandContext<>(actor, manager), CommandInput.empty()).join()
				.forEach(suggestion -> values.add(suggestion.suggestion()));
		return values;
	}

	private static CommandDefinition definition(String usage, String... aliases) {
		return CommandDefinition.builder().usage(usage).aliases(List.of(aliases)).build();
	}

	private static final class FeatureCommand {
		@Definition("feature")
		@Command("identica verify")
		public void execute(@NotNull Actor actor) {
		}
	}

	private static final class ProviderCommand {
		@Definition("provider")
		@Command("identica login")
		public void execute(@NotNull Actor actor) {
		}
	}

	private static final class TestCommandManager extends CommandManager<Actor> {
		private boolean deletionSupported = true;

		private TestCommandManager(@NotNull CommandRegistrationHandler<Actor> registrationHandler) {
			super(ExecutionCoordinator.simpleCoordinator(), registrationHandler);
			registerCapability(CloudCapability.StandardCapabilities.ROOT_COMMAND_DELETION);
		}

		private void lock() {
			lockRegistration();
		}

		@Override
		public boolean hasCapability(@NotNull CloudCapability capability) {
			return deletionSupported && super.hasCapability(capability);
		}

		@Override
		public boolean hasPermission(@NotNull Actor sender, @NotNull String permission) {
			return true;
		}
	}
}
