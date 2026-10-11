package me.whereareiam.identica.engine.step;

import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.common.config.defaults.EngineDefaults;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.model.registration.RegistrationContext;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import me.whereareiam.identica.type.provider.ProviderOrigin;
import me.whereareiam.identica.type.provider.ProviderState;
import me.whereareiam.keystone.model.SerializerContent;
import me.whereareiam.keystone.model.SerializerOptions;
import me.whereareiam.keystone.serializer.SerializerEngine;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

	@DisplayName("Automatically selects the only eligible provider when auto-selection is enabled")
	@Test
	void autoSelectsSingleProviderWhenEnabled() {
		Engine settings = settings(true);
		EnrollmentStep step = new EnrollmentStep(
				providerOperations,
				() -> settings,
				this::messages,
				eventManager
		);
		RegistrationContext context = context();

		when(providerOperations.eligibleProviders(any(), eq(PipelineType.REGISTRATION), eq(JourneyMode.INTERACTIVE)))
				.thenReturn(List.of(provider("premium", "Premium")));

		StepResult result = step.execute(context).join();

		assertEquals(StepResult.StepStatus.COMPLETE, result.getStatus());
		assertNull(result.getMessage());
		assertEquals(context, result.getUpdatedContext());
		assertNotNull(context.getProvider());
		assertEquals("premium", context.getProvider().getProviderId());
		assertEquals("PlayerOne", context.getProvider().getProviderUsername());
		assertEquals(ProviderOrigin.AUTO, context.getProvider().getSource());
		verify(eventManager).call(any());
	}

	@DisplayName("Keeps the enrollment prompt open when auto-selection is disabled")
	@Test
	void keepsWaitingPromptWhenAutoSelectionDisabled() {
		Engine settings = settings(false);
		EnrollmentStep step = new EnrollmentStep(
				providerOperations,
				() -> settings,
				this::messages,
				eventManager
		);
		RegistrationContext context = context();

		when(providerOperations.eligibleProviders(any(), eq(PipelineType.REGISTRATION), eq(JourneyMode.INTERACTIVE)))
				.thenReturn(List.of(provider("premium", "Premium")));

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
		EnrollmentStep step = new EnrollmentStep(
				providerOperations,
				() -> settings,
				this::messages,
				eventManager
		);
		RegistrationContext context = context();

		when(providerOperations.eligibleProviders(any(), eq(PipelineType.REGISTRATION), eq(JourneyMode.INTERACTIVE)))
				.thenReturn(List.of(
						provider("premium", "Premium"),
						provider("credential", "Credential")
				));

		StepResult result = step.execute(context).join();

		assertEquals(StepResult.StepStatus.WAITING, result.getStatus());
		assertNull(context.getProvider());
		verify(eventManager).call(any());
	}

	@DisplayName("Skips the choice for a player who joined through an offered provider's hostname")
	@Test
	void usesTheProviderOfTheHostname() {
		RegistrationContext context = context();
		context.setProvider(ProviderContext.builder()
				.providerId("credential")
				.providerUsername("PlayerOne")
				.source(ProviderOrigin.ENTRYPOINT)
				.build());

		StepResult result = withBothProviders(context);

		assertEquals(StepResult.StepStatus.COMPLETE, result.getStatus());
		assertEquals("credential", context.getProvider().getProviderId());
		assertEquals(ProviderOrigin.ENTRYPOINT, context.getProvider().getSource());
	}

	@DisplayName("Offers the choice when the hostname's provider is not one of the choices")
	@Test
	void offersTheChoiceWhenTheHostnameProviderIsNotOffered() {
		RegistrationContext context = context();
		context.setProvider(ProviderContext.builder()
				.providerId("token")
				.providerUsername("PlayerOne")
				.source(ProviderOrigin.ENTRYPOINT)
				.build());

		assertEquals(StepResult.StepStatus.WAITING, withBothProviders(context).getStatus());
	}

	@DisplayName("Offers the choice for a provider that was not selected through its hostname")
	@Test
	void offersTheChoiceForAProviderSelectedOtherwise() {
		RegistrationContext context = context();
		context.setProvider(ProviderContext.builder()
				.providerId("credential")
				.providerUsername("PlayerOne")
				.source(ProviderOrigin.AUTO)
				.build());

		assertEquals(StepResult.StepStatus.WAITING, withBothProviders(context).getStatus());
	}

	private StepResult withBothProviders(RegistrationContext context) {
		Engine settings = settings(false);
		EnrollmentStep step = new EnrollmentStep(
				providerOperations,
				() -> settings,
				this::messages,
				eventManager
		);
		when(providerOperations.eligibleProviders(any(), eq(PipelineType.REGISTRATION), eq(JourneyMode.INTERACTIVE)))
				.thenReturn(List.of(
						provider("premium", "Premium"),
						provider("credential", "Credential")
				));

		return step.execute(context).join();
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
