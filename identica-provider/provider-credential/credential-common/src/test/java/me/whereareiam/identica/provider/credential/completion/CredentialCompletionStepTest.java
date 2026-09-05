package me.whereareiam.identica.provider.credential.completion;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provider;
import me.whereareiam.identica.feature.FeatureRegistry;
import me.whereareiam.identica.feature.recognition.store.RecognizedConnectionStore;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.pipeline.completion.CompletionContext;
import me.whereareiam.identica.provider.credential.config.CredentialMessages;
import me.whereareiam.identica.provider.credential.config.defaults.CredentialMessagesDefaults;
import me.whereareiam.identica.type.pipeline.PipelineType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Credential Completion Step")
class CredentialCompletionStepTest {
	@DisplayName("Uses the recognition completion message for recognized authentication")
	@Test
	void authenticationUsesRecognitionMessageWhenAuthenticationRecognized() {
		CredentialMessages messages = new CredentialMessagesDefaults().supply(new CredentialMessages());
		InspectableCredentialCompletionStep step = new InspectableCredentialCompletionStep(() -> messages, recognizedStore(true));

		List<String> lines = step.lines(context(true, PipelineType.AUTHENTICATION));

		assertEquals(messages.getCompletion().getRecognition().getBody(), lines);
	}

	@DisplayName("Uses the authentication completion message for authentication pipelines")
	@Test
	void authenticationUsesAuthenticationCompletionMessage() {
		CredentialMessages messages = new CredentialMessagesDefaults().supply(new CredentialMessages());
		InspectableCredentialCompletionStep step = new InspectableCredentialCompletionStep(() -> messages, recognizedStore(false));

		List<String> lines = step.lines(context(false, PipelineType.AUTHENTICATION));

		assertEquals(messages.getCompletion().getAuthentication().getBody(), lines);
	}

	@DisplayName("Uses the registration completion message for registration pipelines")
	@Test
	void registrationUsesRegistrationCompletionMessage() {
		CredentialMessages messages = new CredentialMessagesDefaults().supply(new CredentialMessages());
		InspectableCredentialCompletionStep step = new InspectableCredentialCompletionStep(() -> messages, recognizedStore(false));

		List<String> lines = step.lines(context(false, PipelineType.REGISTRATION));

		assertEquals(messages.getCompletion().getRegistration().getBody(), lines);
	}

	@DisplayName("Uses the migration completion message for migration pipelines")
	@Test
	void migrationUsesMigrationCompletionMessage() {
		CredentialMessages messages = new CredentialMessagesDefaults().supply(new CredentialMessages());
		InspectableCredentialCompletionStep step = new InspectableCredentialCompletionStep(() -> messages, recognizedStore(true));

		List<String> lines = step.lines(context(true, PipelineType.MIGRATION));

		assertEquals(messages.getCompletion().getMigration().getBody(), lines);
	}

	@DisplayName("Migration completion copy announces credential migration completion")
	@Test
	void migrationCompletionCopyAnnouncesCredentialMigrationCompletion() {
		CredentialMessages messages = new CredentialMessagesDefaults().supply(new CredentialMessages());
		InspectableCredentialCompletionStep step = new InspectableCredentialCompletionStep(() -> messages, recognizedStore(true));

		List<String> lines = step.lines(context(true, PipelineType.MIGRATION));

		assertTrue(lines.contains("  <white>Credential provider migration <green>completed</green>.</white>"));
		assertFalse(lines.stream().anyMatch(line -> line.contains("Welcome back")));
	}

	@Test
	void authenticationCompletesWithoutRecognitionBindings() {
		CredentialMessages messages = new CredentialMessagesDefaults().supply(new CredentialMessages());
		Injector injector = Guice.createInjector();
		InspectableCredentialCompletionStep step = new InspectableCredentialCompletionStep(() -> messages, features(false), injector);

		assertEquals(messages.getCompletion().getAuthentication().getBody(), step.lines(context(false, PipelineType.AUTHENTICATION)));
	}

	private static FeatureRegistry features(boolean installed) {
		FeatureRegistry features = mock(FeatureRegistry.class);
		when(features.isEnabled("credential", "recognition")).thenReturn(installed);
		return features;
	}

	private static Injector injector(RecognizedConnectionStore store) {
		return Guice.createInjector(binder -> binder.bind(RecognizedConnectionStore.class).toInstance(store));
	}

	private CompletionContext context(boolean recognized, PipelineType pipelineType) {
		TestIdentity identity = new TestIdentity();
		return CompletionContext.builder()
				.identity(identity)
				.pipelineType(pipelineType)
				.session(Session.builder()
						.uniqueId(UUID.randomUUID())
						.providerId("credential")
						.providerSubject("player-one")
						.originalUsername("PlayerOne")
						.effectiveUsername("PlayerOne")
						.build())
				.build();
	}

	private RecognizedConnectionStore recognizedStore(boolean recognized) {
		RecognizedConnectionStore store = mock(RecognizedConnectionStore.class);
		when(store.isRecognized(any())).thenReturn(recognized);
		return store;
	}

	private static final class InspectableCredentialCompletionStep extends CredentialCompletionStep {
		private InspectableCredentialCompletionStep(
				Provider<CredentialMessages> messagesProvider,
				RecognizedConnectionStore recognizedConnectionStore
		) {
			super(messagesProvider, features(true), injector(recognizedConnectionStore));
		}

		private InspectableCredentialCompletionStep(Provider<CredentialMessages> messagesProvider, FeatureRegistry features, Injector injector) {
			super(messagesProvider, features, injector);
		}

		private List<String> lines(CompletionContext context) {
			return messageLines(context);
		}
	}

	private static final class TestIdentity extends Identity {
		private TestIdentity() {
			super(UUID.randomUUID(), UUID.randomUUID(), "PlayerOne", "127.0.0.1");
		}

		@Override
		public void sendMessage(@NonNull Component message) {
		}

		@Override
		public void sendTitle(@NonNull Title title) {
		}

		@Override
		public boolean hasPermission(@NonNull String permission) {
			return true;
		}

		@Override
		public @NonNull Locale getLocale() {
			return Locale.ENGLISH;
		}

		@Override
		public @NonNull Audience getAudience() {
			return Audience.empty();
		}

		@Override
		public void disconnect(@NonNull Component reason) {
		}
	}
}
