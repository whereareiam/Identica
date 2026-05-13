package me.whereareiam.identica.provider.password.completion;

import com.google.inject.Provider;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.pipeline.completion.CompletionContext;
import me.whereareiam.identica.provider.password.config.PasswordMessages;
import me.whereareiam.identica.provider.password.config.defaults.PasswordMessagesDefaults;
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

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Password Completion Step")
class PasswordCompletionStepTest {
	@DisplayName("Uses the reused-session completion message for authentication resumes")
	@Test
	void authenticationUsesReusedSessionMessageWhenSessionWasReused() {
		PasswordMessages messages = new PasswordMessagesDefaults().supply(new PasswordMessages());
		InspectablePasswordCompletionStep step = new InspectablePasswordCompletionStep(() -> messages);

		List<String> lines = step.lines(context(true, PipelineType.AUTHENTICATION));

		assertEquals(messages.getCompletion().getSession().getBody(), lines);
	}

	@DisplayName("Uses the authentication completion message for authentication pipelines")
	@Test
	void authenticationUsesAuthenticationCompletionMessage() {
		PasswordMessages messages = new PasswordMessagesDefaults().supply(new PasswordMessages());
		InspectablePasswordCompletionStep step = new InspectablePasswordCompletionStep(() -> messages);

		List<String> lines = step.lines(context(false, PipelineType.AUTHENTICATION));

		assertEquals(messages.getCompletion().getAuthentication().getBody(), lines);
	}

	@DisplayName("Uses the registration completion message for registration pipelines")
	@Test
	void registrationUsesRegistrationCompletionMessage() {
		PasswordMessages messages = new PasswordMessagesDefaults().supply(new PasswordMessages());
		InspectablePasswordCompletionStep step = new InspectablePasswordCompletionStep(() -> messages);

		List<String> lines = step.lines(context(false, PipelineType.REGISTRATION));

		assertEquals(messages.getCompletion().getRegistration().getBody(), lines);
	}

	@DisplayName("Uses the migration completion message for migration pipelines")
	@Test
	void migrationUsesMigrationCompletionMessage() {
		PasswordMessages messages = new PasswordMessagesDefaults().supply(new PasswordMessages());
		InspectablePasswordCompletionStep step = new InspectablePasswordCompletionStep(() -> messages);

		List<String> lines = step.lines(context(true, PipelineType.MIGRATION));

		assertEquals(messages.getCompletion().getMigration().getBody(), lines);
	}

	private CompletionContext context(boolean sessionReused, PipelineType pipelineType) {
		return CompletionContext.builder()
				.identity(new TestIdentity())
				.pipelineType(pipelineType)
				.session(Session.builder()
						.uniqueId(UUID.randomUUID())
						.providerId("password")
						.providerSubject("player-one")
						.originalUsername("PlayerOne")
						.effectiveUsername("PlayerOne")
						.build())
				.sessionReused(sessionReused)
				.build();
	}

	private static final class InspectablePasswordCompletionStep extends PasswordCompletionStep {
		private InspectablePasswordCompletionStep(Provider<PasswordMessages> messagesProvider) {
			super(messagesProvider);
		}

		private List<String> lines(CompletionContext context) {
			return messageLines(context);
		}
	}

	private static final class TestIdentity extends Identity {
		private TestIdentity() {
			super(UUID.randomUUID(), "PlayerOne");
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
