package me.whereareiam.identica.engine.step;

import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.common.config.defaults.EngineDefaults;
import me.whereareiam.identica.common.provider.selection.DefaultProviderSelector;
import me.whereareiam.identica.common.provider.selection.ProviderClaimRanking;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.model.provider.selection.ProviderClaim;
import me.whereareiam.identica.model.provider.selection.ProviderSelection;
import me.whereareiam.identica.model.registration.RegistrationContext;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.engine.pipeline.scenario.shared.group.journey.option.JourneyOptions;
import me.whereareiam.identica.model.config.provider.Providers;
import me.whereareiam.identica.model.pipeline.journey.ProviderMethod;
import me.whereareiam.identica.pipeline.journey.registry.type.AuthenticationJourneyRegistry;
import me.whereareiam.identica.pipeline.journey.registry.type.MigrationJourneyRegistry;
import me.whereareiam.identica.pipeline.journey.registry.type.RegistrationJourneyRegistry;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.type.pipeline.journey.JourneyChoice;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import me.whereareiam.identica.type.provider.ProviderState;
import me.whereareiam.keystone.model.SerializerContent;
import me.whereareiam.keystone.model.SerializerOptions;
import me.whereareiam.keystone.serializer.SerializerEngine;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Enrollment Step")
class EnrollmentStepTest {
	@BeforeAll
	static void initializeSerializer() {
		Serializer.initialize(() -> TEST_SERIALIZER);
	}

	private static final SerializerEngine TEST_SERIALIZER = new SerializerEngine() {
		@Override
		public @NotNull String serialize(Component component) {
			return component.toString();
		}

		@Override
		public @NotNull Component serialize(SerializerContent content) {
			String message = content.getMessage();
			return Component.text(message);
		}

		public @NotNull String renderTemplate(@NotNull SerializerContent content) {
			return content.getMessage();
		}

		@Override
		public @NotNull SerializerOptions.PlaceholderFormat getPlaceholderFormat() {
			return SerializerOptions.PlaceholderFormat.CURLY_BRACES;
		}
	};

	@Mock
	private ProviderOperations providerOperations;
	@Mock
	private EventManager eventManager;
	@Mock
	private ProviderManager providerManager;
	@Mock(strictness = Mock.Strictness.LENIENT)
	private RegistrationJourneyRegistry registrationJourneyRegistry;

	@BeforeEach
	void enableProviders() {
		lenient().when(providerManager.getProviders()).thenReturn(List.of(
				provider("alpha", "Alpha"),
				provider("beta", "Beta"),
				provider("gamma", "Gamma")
		));
	}

	@DisplayName("Picks the only option without asking")
	@Test
	void picksTheOnlyOptionWithoutAsking() {
		RegistrationContext context = context();
		offer("alpha");

		StepResult result = run(step(settings(JourneyChoice.PROMPTED)), context);

		assertEquals(StepResult.StepStatus.COMPLETE, result.getStatus());
		assertNull(result.getMessage());
		assertEquals(context, result.getUpdatedContext());
		assertNotNull(context.getProvider());
		assertEquals("alpha", context.getProvider().getProviderId());
		assertEquals("PlayerOne", context.getProvider().getProviderUsername());
		verify(eventManager).call(any());
	}

	@DisplayName("Leaves the options to the journey when Identica picks")
	@Test
	void leavesTheOptionsToTheJourneyWhenAutomatic() {
		RegistrationContext context = context();

		StepResult result = run(step(settings(JourneyChoice.AUTOMATIC)), context);

		assertEquals(StepResult.StepStatus.CONTINUE, result.getStatus());
		assertNull(context.getProvider());
		verify(eventManager, never()).call(any());
	}

	@DisplayName("Asks the player when more than one option is available")
	@Test
	void asksWhenSeveralOptionsRemain() {
		RegistrationContext context = context();
		offer("alpha", "beta");

		StepResult result = run(step(settings(JourneyChoice.PROMPTED)), context);

		assertEquals(StepResult.StepStatus.WAITING, result.getStatus());
		assertNotNull(result.getMessage());
		assertFalse(result.getMessage().isBlank());
		assertEquals(EnrollmentStep.PROMPT, result.getPrompt());
		assertNull(context.getProvider());
		verify(eventManager).call(any());
	}

	@DisplayName("Offers each method of a provider that has several as an option of its own")
	@Test
	void offersEachMethodOfAProviderWithSeveral() {
		RegistrationContext context = context();
		offer("alpha", "beta");
		when(registrationJourneyRegistry.methods("beta")).thenReturn(List.of(method("beta", "quick"), method("beta", "guided")));

		StepResult result = run(step(settings(JourneyChoice.PROMPTED)), context);

		assertEquals(StepResult.StepStatus.WAITING, result.getStatus());
		assertTrue(result.getMessage().contains("enroll alpha>"));
		assertTrue(result.getMessage().contains("enroll beta:quick>"));
		assertTrue(result.getMessage().contains("enroll beta:guided>"));
	}

	@DisplayName("Asks only among the methods of the provider its hostname selected")
	@Test
	void asksAmongTheMethodsOfTheEntrypointProvider() {
		RegistrationContext context = context(ProviderClaim.of("beta", ProviderClaimOrigin.HOSTNAME));
		offer("alpha", "beta");
		when(registrationJourneyRegistry.methods("beta")).thenReturn(List.of(method("beta", "quick"), method("beta", "guided")));

		StepResult result = run(step(settings(JourneyChoice.PROMPTED)), context);

		assertEquals(StepResult.StepStatus.WAITING, result.getStatus());
		assertFalse(result.getMessage().contains("enroll alpha>"));
		assertTrue(result.getMessage().contains("enroll beta:quick>"));
	}

	@DisplayName("Skips the choice for an offered provider its hostname selected")
	@Test
	void skipsTheChoiceForAnOfferedEntrypointProvider() {
		RegistrationContext context = context(ProviderClaim.of("beta", ProviderClaimOrigin.HOSTNAME));
		offer("alpha", "beta");

		StepResult result = run(step(settings(JourneyChoice.PROMPTED)), context);

		assertEquals(StepResult.StepStatus.COMPLETE, result.getStatus());
		assertEquals("beta", context.getProvider().getProviderId());
	}

	@DisplayName("Skips the choice for an offered provider whose verified subject is linked to an account")
	@Test
	void skipsTheChoiceForAnOfferedLinkedProvider() {
		RegistrationContext context = context(
				ProviderClaim.of("alpha", ProviderClaimOrigin.HOSTNAME),
				ProviderClaim.of("beta", ProviderClaimOrigin.ACCOUNT_LINK)
		);
		offer("alpha", "beta");

		StepResult result = run(step(settings(JourneyChoice.PROMPTED)), context);

		assertEquals(StepResult.StepStatus.COMPLETE, result.getStatus());
		assertEquals("beta", context.getProvider().getProviderId());
	}

	@DisplayName("Shows the choice when the hostname selected a provider that is not offered")
	@Test
	void showsTheChoiceWhenTheEntrypointProviderIsNotOffered() {
		RegistrationContext context = context(ProviderClaim.of("gamma", ProviderClaimOrigin.HOSTNAME));
		offer("alpha", "beta");

		StepResult result = run(step(settings(JourneyChoice.PROMPTED)), context);

		assertEquals(StepResult.StepStatus.WAITING, result.getStatus());
		assertNull(context.getProvider());
	}

	@DisplayName("Shows the choice when only a claim weaker than a hostname selects a provider")
	@Test
	void showsTheChoiceForAWeakerClaim() {
		RegistrationContext context = context(
				ProviderClaim.verified("alpha", "alpha-proof"),
				ProviderClaim.of("beta", ProviderClaimOrigin.DISCOVERY)
		);
		offer("alpha", "beta");

		StepResult result = run(step(settings(JourneyChoice.PROMPTED)), context);

		assertEquals(StepResult.StepStatus.WAITING, result.getStatus());
		assertNull(context.getProvider());
	}

	private void offer(String... providerIds) {
		List<InternalProvider> providers = Arrays.stream(providerIds)
				.map(id -> provider(id, id.toUpperCase(Locale.ROOT)))
				.toList();
		when(providerOperations.eligibleProviders(any(), eq(PipelineType.REGISTRATION)))
				.thenReturn(providers);
	}

	private StepResult run(EnrollmentStep step, RegistrationContext context) {
		PipelineState state = PipelineState.initial();
		state.setPipelineType(PipelineType.REGISTRATION);
		state.setScenario(context);

		return step.execute(context, state).join();
	}

	private EnrollmentStep step(Engine settings) {
		DefaultProviderSelector selector = new DefaultProviderSelector(new ProviderClaimRanking(providerManager));
		JourneyOptions options = new JourneyOptions(
				providerOperations,
				selector,
				() -> mock(AuthenticationJourneyRegistry.class),
				() -> registrationJourneyRegistry,
				() -> mock(MigrationJourneyRegistry.class),
				Providers::new
		);

		return new EnrollmentStep(
				options,
				providerOperations,
				() -> settings,
				this::messages,
				eventManager,
				selector
		);
	}

	private ProviderMethod method(String providerId, String name) {
		return ProviderMethod.builder()
				.providerId(providerId)
				.name(name)
				.scenario(PipelineType.REGISTRATION)
				.build();
	}

	private RegistrationContext context(ProviderClaim... claims) {
		RegistrationContext context = context();
		context.setSelection(new DefaultProviderSelector(new ProviderClaimRanking(providerManager))
				.claim(ProviderSelection.empty(), List.of(claims)));
		return context;
	}

	private Engine settings(JourneyChoice choice) {
		Engine settings = new EngineDefaults().supply(new Engine());
		settings.getScenarios().getRegistration().setChoice(choice);
		return settings;
	}

	private Messages messages() {
		Messages messages = new Messages();
		Messages.Engine connection = new Messages.Engine();
		Messages.Engine.Journey journey = new Messages.Engine.Journey();
		Messages.Engine.Journey.Stage stage = new Messages.Engine.Journey.Stage();
		stage.setNoCompletion(List.of("no-completion"));
		journey.setStage(stage);

		Messages.Engine.Journey.Step step = new Messages.Engine.Journey.Step();
		step.setNoStatus(List.of("no-status"));
		Messages.Engine.Journey.Step.Enrollment enrollment = new Messages.Engine.Journey.Step.Enrollment();
		enrollment.setBody(List.of(
				" ",
				" <green><bold>Identica</bold>",
				" ",
				"  <white>Select an authentication method for account</white>",
				"  <white>It can be changed later on.</white>",
				" ",
				"  <gray>Available providers:</gray>",
				"{entries}",
				" "
		));
		Messages.Engine.Journey.Step.Enrollment.EntryFormat entryFormat =
				new Messages.Engine.Journey.Step.Enrollment.EntryFormat();
		entryFormat.setFormat("   <dark_gray><click:run_command:/identica enroll {optionId}>▪ <gray>[{optionName}]:</gray> <white>{description}</click>");
		entryFormat.setEmptyFormat("   <dark_gray><click:run_command:/identica enroll {optionId}>▪ <gray>[{optionName}]:</gray></click>");
		enrollment.setEntryFormat(entryFormat);
		enrollment.setEmpty(List.of("empty"));
		enrollment.setDescriptions(java.util.Map.of());
		step.setEnrollment(enrollment);
		journey.setStep(step);
		connection.setJourney(journey);
		messages.setEngine(connection);
		return messages;
	}

	private RegistrationContext context() {
		return RegistrationContext.builder()
				.connectionUniqueId(UUID.randomUUID())
				.identity(new ConnectionIdentity(UUID.randomUUID(), "PlayerOne", "127.0.0.1"))
				.build();
	}

	private InternalProvider provider(String providerId, String providerName) {
		ProviderDescriptor descriptor = new ProviderDescriptor();
		descriptor.setId(providerId);
		descriptor.setName(providerName);
		descriptor.setVersion("1.0.0");
		descriptor.setMain("example.Main");
		descriptor.setSupportedPlatforms(List.of("velocity"));

		return InternalProvider.builder()
				.descriptor(descriptor)
				.state(ProviderState.ENABLED)
				.build();
	}
}
