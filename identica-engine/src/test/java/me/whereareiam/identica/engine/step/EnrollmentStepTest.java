package me.whereareiam.identica.engine.step;

import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.common.config.defaults.SettingsDefaults;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.config.Settings;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.model.provider.InternalProvider;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
		Settings settings = settings(true);
		EnrollmentStep step = new EnrollmentStep(
				providerOperations,
				() -> settings,
				this::messages,
				eventManager
		);
		RegistrationContext context = context("PlayerOne");

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
		Settings settings = settings(false);
		EnrollmentStep step = new EnrollmentStep(
				providerOperations,
				() -> settings,
				this::messages,
				eventManager
		);
		RegistrationContext context = context("PlayerOne");

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
		Settings settings = settings(true);
		EnrollmentStep step = new EnrollmentStep(
				providerOperations,
				() -> settings,
				this::messages,
				eventManager
		);
		RegistrationContext context = context("PlayerOne");

		when(providerOperations.eligibleProviders(any(), eq(PipelineType.REGISTRATION), eq(JourneyMode.INTERACTIVE)))
				.thenReturn(List.of(
						provider("premium", "Premium"),
						provider("password", "Password")
				));

		StepResult result = step.execute(context).join();

		assertEquals(StepResult.StepStatus.WAITING, result.getStatus());
		assertNull(context.getProvider());
		verify(eventManager).call(any());
	}

	private Settings settings(boolean autoSelectSingleProvider) {
		Settings settings = new SettingsDefaults().supply(new Settings());
		settings.getConnection().getRegistration().setAutoSelectSingleProvider(autoSelectSingleProvider);
		return settings;
	}

	private Messages messages() {
		Messages messages = new Messages();
		Messages.Connection connection = new Messages.Connection();
		Messages.Connection.Journey journey = new Messages.Connection.Journey();
		Messages.Connection.Journey.Stage stage = new Messages.Connection.Journey.Stage();
		stage.setNoCompletion(List.of("no-completion"));
		journey.setStage(stage);

		Messages.Connection.Journey.Step step = new Messages.Connection.Journey.Step();
		step.setNoStatus(List.of("no-status"));
		Messages.Connection.Journey.Step.Enrollment enrollment = new Messages.Connection.Journey.Step.Enrollment();
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
		Messages.Connection.Journey.Step.Enrollment.EntryFormat entryFormat =
				new Messages.Connection.Journey.Step.Enrollment.EntryFormat();
		entryFormat.setFormat("   <dark_gray><click:run_command:/identica enroll {providerId}>▪ <gray>[{providerName}]:</gray> <white>{description}</click>");
		entryFormat.setEmptyFormat("   <dark_gray><click:run_command:/identica enroll {providerId}>▪ <gray>[{providerName}]:</gray></click>");
		enrollment.setEntryFormat(entryFormat);
		enrollment.setEmpty(List.of("empty"));
		enrollment.setDescriptions(java.util.Map.of());
		step.setEnrollment(enrollment);
		journey.setStep(step);
		connection.setJourney(journey);
		messages.setConnection(connection);
		return messages;
	}

	private RegistrationContext context(String username) {
		return RegistrationContext.builder()
				.connectionUniqueId(UUID.randomUUID())
				.identity(new ConnectionIdentity(UUID.randomUUID(), username, "127.0.0.1"))
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
