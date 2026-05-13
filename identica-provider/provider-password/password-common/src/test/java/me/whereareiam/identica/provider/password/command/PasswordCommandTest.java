package me.whereareiam.identica.provider.password.command;

import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.migration.PendingMigration;
import me.whereareiam.identica.model.migration.operation.MigrationConfirm;
import me.whereareiam.identica.model.migration.operation.MigrationResult;
import me.whereareiam.identica.model.verification.VerificationResolutionResult;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.provider.password.config.PasswordMessages;
import me.whereareiam.identica.provider.password.config.defaults.PasswordMessagesDefaults;
import me.whereareiam.identica.service.MigrationService;
import me.whereareiam.identica.type.migration.MigrationResultStatus;
import me.whereareiam.identica.type.verification.VerificationResolutionStatus;
import me.whereareiam.identica.verification.VerificationService;
import me.whereareiam.keystone.model.SerializerContent;
import me.whereareiam.keystone.model.SerializerOptions;
import me.whereareiam.keystone.serializer.SerializerEngine;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Password Command")
class PasswordCommandTest {
	@BeforeAll
	static void initializeSerializer() {
		Serializer.initialize(() -> TEST_SERIALIZER);
	}

	private static final SerializerEngine TEST_SERIALIZER = new SerializerEngine() {
		@Override
		public @NotNull String serialize(@NotNull Component component) {
			return component.toString();
		}

		@Override
		public @NotNull Component serialize(@NotNull SerializerContent content) {
			return Component.text(content.getMessage());
		}

		@Override
		public @NotNull SerializerOptions.PlaceholderFormat getPlaceholderFormat() {
			return SerializerOptions.PlaceholderFormat.CURLY_BRACES;
		}
	};

	@Mock
	private MigrationService migrationService;
	@Mock
	private ProviderManager providerManager;
	@Mock
	private VerificationService verificationService;
	@Mock
	private SessionService sessionService;

	@Test
	@DisplayName("Requests the verification code instead of failing when step-up is required and no input was provided")
	void confirmWithoutInputRequestsVerificationCodeWhenStepUpIsRequired() {
		TestIdentity identity = new TestIdentity();
		when(sessionService.findByUniqueId(identity.getUniqueId())).thenReturn(CompletableFuture.completedFuture(Optional.of(session(identity))));
		when(migrationService.findPendingMigration(identity.getUniqueId())).thenReturn(Optional.of(pendingMigration()));
		when(verificationService.findEnrollments(identity.getUniqueId())).thenReturn(List.of(mockEnrollment()));
		when(verificationService.resolveVerification(any()))
				.thenReturn(VerificationResolutionResult.of(VerificationResolutionStatus.WAITING, "challenge", "totp", true, false));

		PasswordMessages passwordMessages = new PasswordMessagesDefaults().supply(new PasswordMessages());
		PasswordCommand command = new PasswordCommand(
				() -> passwordMessages,
				Messages::new,
				migrationService,
				providerManager,
				verificationService,
				sessionService
		);

		command.confirm(identity, null);

		assertEquals(
				passwordMessages.getCommands().getPassword().getVerificationRequired(),
				PlainTextComponentSerializer.plainText().serialize(identity.lastMessage())
		);
		verify(migrationService, never()).confirm(any(MigrationConfirm.class));
	}

	@Test
	@DisplayName("Continues the migration after a successful step-up confirmation code")
	void confirmWithInputContinuesMigrationAfterSuccessfulStepUp() {
		TestIdentity identity = new TestIdentity();
		when(sessionService.findByUniqueId(identity.getUniqueId())).thenReturn(CompletableFuture.completedFuture(Optional.of(session(identity))));
		when(migrationService.findPendingMigration(identity.getUniqueId())).thenReturn(Optional.of(pendingMigration()));
		when(verificationService.findEnrollments(identity.getUniqueId())).thenReturn(List.of(mockEnrollment()));
		when(verificationService.resolveVerification(any()))
				.thenReturn(VerificationResolutionResult.of(VerificationResolutionStatus.WAITING, "challenge", "totp", true, false));
		when(verificationService.submitChallenge(eq(identity.getUniqueId()), eq("password"), eq("migration-confirm"), any()))
				.thenReturn(me.whereareiam.identica.model.verification.challenge.VerificationChallengeResult.verified(null));
		when(migrationService.confirm(any(MigrationConfirm.class)))
				.thenReturn(MigrationResult.builder().status(MigrationResultStatus.STARTED).build());

		PasswordCommand command = new PasswordCommand(
				() -> new PasswordMessagesDefaults().supply(new PasswordMessages()),
				Messages::new,
				migrationService,
				providerManager,
				verificationService,
				sessionService
		);

		command.confirm(identity, "420683");

		verify(migrationService).confirm(any(MigrationConfirm.class));
	}

	private Session session(TestIdentity identity) {
		return Session.builder()
				.uniqueId(identity.getUniqueId())
				.providerId("password")
				.providerSubject("password-subject")
				.originalUsername(identity.getUsername())
				.effectiveUsername(identity.getUsername())
				.build();
	}

	private PendingMigration pendingMigration() {
		return PendingMigration.builder()
				.phase(PendingMigration.Phase.CONFIRMATION)
				.build();
	}

	private me.whereareiam.identica.model.verification.enrollment.VerificationEnrollment mockEnrollment() {
		return me.whereareiam.identica.model.verification.enrollment.VerificationEnrollment.builder()
				.methodId("totp")
				.build();
	}

	private static final class TestIdentity extends Identity {
		private final AtomicReference<Component> lastMessage = new AtomicReference<>();

		private TestIdentity() {
			super(UUID.randomUUID(), "PlayerOne");
		}

		@Override
		public void sendMessage(@NotNull Component message) {
			lastMessage.set(message);
		}

		@Override
		public void sendTitle(@NotNull Title title) {
		}

		@Override
		public boolean hasPermission(@NotNull String permission) {
			return true;
		}

		@Override
		public @NotNull Locale getLocale() {
			return Locale.ENGLISH;
		}

		@Override
		public @NotNull Audience getAudience() {
			return Audience.empty();
		}

		@Override
		public void disconnect(@NotNull Component reason) {
		}

		private Component lastMessage() {
			return lastMessage.get();
		}
	}
}
