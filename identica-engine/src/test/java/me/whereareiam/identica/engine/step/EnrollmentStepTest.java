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
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
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

	@BeforeEach
	void enableProviders() {
		lenient().when(providerManager.getProviders()).thenReturn(List.of(
				provider("alpha", "Alpha"),
				provider("beta", "Beta"),
				provider("gamma", "Gamma")
		));
	}

	@DisplayName("Automatically selects the only eligible provider when auto-selection is enabled")
	@Test
	void autoSelectsSingleProviderWhenEnabled() {
		Engine settings = settings(true);
		EnrollmentStep step = step(settings);
		RegistrationContext context = context();

		when(providerOperations.eligibleProviders(any(), eq(PipelineType.REGISTRATION), eq(JourneyMode.INTERACTIVE)))
				.thenReturn(List.of(provider("alpha", "Alpha")));

		StepResult result = step.execute(context).join();

		assertEquals(StepResult.StepStatus.COMPLETE, result.getStatus());
		assertNull(result.getMessage());
		assertEquals(context, result.getUpdatedContext());
		assertNotNull(context.getProvider());
		assertEquals("alpha", context.getProvider().getProviderId());
		assertEquals("PlayerOne", context.getProvider().getProviderUsername());
		verify(eventManager).call(any());
	}

	@DisplayName("Keeps the enrollment prompt open when auto-selection is disabled")
	@Test
	void keepsWaitingPromptWhenAutoSelectionDisabled() {
		Engine settings = settings(false);
		EnrollmentStep step = step(settings);
		RegistrationContext context = context();

		when(providerOperations.eligibleProviders(any(), eq(PipelineType.REGISTRATION), eq(JourneyMode.INTERACTIVE)))
				.thenReturn(List.of(provider("alpha", "Alpha")));

		StepResult result = step.execute(context).join();

		assertEquals(StepResult.StepStatus.WAITING, result.getStatus());
		assertNotNull(result.getMessage());
		assertFalse(result.getMessage().isBlank());
		assertNull(context.getProvider());
		verify(eventManager).call(any());
	}

	@DisplayName("Keeps the enrollment prompt open when more than one provider is available")
	@Test
	void keepsWaitingPromptWhenMultipleProvidersRemain() {
		Engine settings = settings(true);
		EnrollmentStep step = step(settings);
		RegistrationContext context = context();

		when(providerOperations.eligibleProviders(any(), eq(PipelineType.REGISTRATION), eq(JourneyMode.INTERACTIVE)))
				.thenReturn(List.of(
						provider("alpha", "Alpha"),
						provider("beta", "Beta")
				));

		StepResult result = step.execute(context).join();

		assertEquals(StepResult.StepStatus.WAITING, result.getStatus());
		assertNull(context.getProvider());
		verify(eventManager).call(any());
	}

	@DisplayName("Skips the choice for an offered provider its hostname selected")
	@Test
	void skipsTheChoiceForAnOfferedEntrypointProvider() {
		RegistrationContext context = context(ProviderClaim.of("beta", ProviderClaimOrigin.HOSTNAME));
		offer("alpha", "beta");

		StepResult result = step(settings(true)).execute(context).join();

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

		StepResult result = step(settings(true)).execute(context).join();

		assertEquals(StepResult.StepStatus.COMPLETE, result.getStatus());
		assertEquals("beta", context.getProvider().getProviderId());
	}

	@DisplayName("Shows the choice when the hostname selected a provider that is not offered")
	@Test
	void showsTheChoiceWhenTheEntrypointProviderIsNotOffered() {
		RegistrationContext context = context(ProviderClaim.of("gamma", ProviderClaimOrigin.HOSTNAME));
		offer("alpha", "beta");

		StepResult result = step(settings(true)).execute(context).join();

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

		StepResult result = step(settings(true)).execute(context).join();

		assertEquals(StepResult.StepStatus.WAITING, result.getStatus());
		assertNull(context.getProvider());
	}

	private void offer(String... providerIds) {
		List<InternalProvider> providers = Arrays.stream(providerIds)
				.map(id -> provider(id, id.toUpperCase(Locale.ROOT)))
				.toList();
		when(providerOperations.eligibleProviders(any(), eq(PipelineType.REGISTRATION), eq(JourneyMode.INTERACTIVE)))
				.thenReturn(providers);
	}

	private EnrollmentStep step(Engine settings) {
		return new EnrollmentStep(
				providerOperations,
				() -> settings,
				this::messages,
				eventManager,
				new DefaultProviderSelector(new ProviderClaimRanking(providerManager))
		);
	}

	private RegistrationContext context(ProviderClaim... claims) {
		RegistrationContext context = context();
		context.setSelection(new DefaultProviderSelector(new ProviderClaimRanking(providerManager))
				.claim(ProviderSelection.empty(), List.of(claims)));
		return context;
	}

	private Engine settings(boolean autoSelectSingleProvider) {
		Engine settings = new EngineDefaults().supply(new Engine());
		settings.getScenarios().getRegistration().setAutoSelectSingleProvider(autoSelectSingleProvider);
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
		entryFormat.setFormat("   <dark_gray><click:run_command:/identica enroll {providerId}>▪ <gray>[{providerName}]:</gray> <white>{description}</click>");
		entryFormat.setEmptyFormat("   <dark_gray><click:run_command:/identica enroll {providerId}>▪ <gray>[{providerName}]:</gray></click>");
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
