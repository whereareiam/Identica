package me.whereareiam.identica.provider.premium.command;

import com.google.inject.Guice;
import com.google.inject.Injector;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.feature.FeatureRegistry;
import me.whereareiam.identica.feature.verification.VerificationService;
import me.whereareiam.identica.feature.verification.config.VerificationMessages;
import me.whereareiam.identica.feature.verification.model.resolution.VerificationResolutionResult;
import me.whereareiam.identica.feature.verification.type.status.VerificationResolutionStatus;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.migration.PendingMigration;
import me.whereareiam.identica.model.migration.operation.MigrationConfirm;
import me.whereareiam.identica.model.migration.operation.MigrationResult;
import me.whereareiam.identica.provider.premium.config.PremiumMessages;
import me.whereareiam.identica.provider.premium.config.defaults.PremiumMessagesDefaults;
import me.whereareiam.identica.service.MigrationService;
import me.whereareiam.identica.type.migration.MigrationResultStatus;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Premium Command")
class PremiumCommandTest {
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

		public @NotNull String renderTemplate(@NotNull SerializerContent content) {
			return content.getMessage();
		}

		@Override
		public @NotNull SerializerOptions.PlaceholderFormat getPlaceholderFormat() {
			return SerializerOptions.PlaceholderFormat.CURLY_BRACES;
		}
	};

	@Mock
	private MigrationService migrationService;
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

		PremiumMessages premiumMessages = new PremiumMessagesDefaults().supply(new PremiumMessages());
		PremiumCommand command = new PremiumCommand(
				migrationService,
				() -> premiumMessages,
				Messages::new,
				injector(true),
				sessionService
		);

		command.confirm(identity, null);

		assertEquals(
				premiumMessages.getCommands().getPremium().getVerificationRequired(),
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
		when(verificationService.submitChallenge(eq(identity.getUniqueId()), eq("credential"), eq("migration-confirm"), any()))
				.thenReturn(me.whereareiam.identica.feature.verification.model.challenge.VerificationChallengeResult.verified(null));
		when(migrationService.confirm(any(MigrationConfirm.class)))
				.thenReturn(MigrationResult.builder().status(MigrationResultStatus.STARTED).build());

		PremiumCommand command = new PremiumCommand(
				migrationService,
				() -> new PremiumMessagesDefaults().supply(new PremiumMessages()),
				Messages::new,
				injector(true),
				sessionService
		);

		command.confirm(identity, "420683");

		verify(migrationService).confirm(any(MigrationConfirm.class));
	}

	@Test
	void confirmsWhenCurrentProviderDisablesVerification() {
		TestIdentity identity = new TestIdentity();
		when(sessionService.findByUniqueId(identity.getUniqueId())).thenReturn(CompletableFuture.completedFuture(Optional.of(session(identity))));
		when(migrationService.findPendingMigration(identity.getConnectionUniqueId())).thenReturn(Optional.of(pendingMigration()));
		when(migrationService.confirm(any())).thenReturn(MigrationResult.builder().status(MigrationResultStatus.STARTED).build());
		when(verificationService.findEnrollments(identity.getUniqueId())).thenReturn(List.of(mockEnrollment()));
		verificationPolicy(false);
		Injector injector = Guice.createInjector(binder -> {
			binder.bind(VerificationService.class).toInstance(verificationService);
			binder.bind(MigrationService.class).toInstance(migrationService);
			binder.bind(SessionService.class).toInstance(sessionService);
			binder.bind(Messages.class).toInstance(new Messages());
			binder.bind(PremiumMessages.class).toInstance(new PremiumMessagesDefaults().supply(new PremiumMessages()));
		});

		injector.getInstance(PremiumCommand.class).confirm(identity, null);

		verify(migrationService).confirm(any(MigrationConfirm.class));
		verify(verificationService, never()).resolveVerification(any());
		verify(verificationService).isEnabledForProvider(session(identity).getProviderId());
	}

	private void verificationPolicy(boolean enabled) {
		when(verificationService.isEnabledForProvider(anyString())).thenReturn(enabled);
	}

	private Injector injector(boolean enabled) {
		verificationPolicy(enabled);
		return injector();
	}

	private Injector injector() {
		return Guice.createInjector(binder -> {
			binder.bind(VerificationService.class).toInstance(verificationService);
			binder.bind(VerificationMessages.class).toInstance(verificationMessages());
		});
	}

	private Session session(TestIdentity identity) {
		return Session.builder()
				.uniqueId(identity.getUniqueId())
				.providerId("credential")
				.providerSubject("credential-subject")
				.originalUsername(identity.getUsername())
				.effectiveUsername(identity.getUsername())
				.build();
	}

	private PendingMigration pendingMigration() {
		return PendingMigration.builder()
				.phase(PendingMigration.Phase.CONFIRMATION)
				.build();
	}

	private me.whereareiam.identica.feature.verification.model.enrollment.VerificationEnrollment mockEnrollment() {
		return me.whereareiam.identica.feature.verification.model.enrollment.VerificationEnrollment.builder()
				.methodId("totp")
				.build();
	}

	private VerificationMessages verificationMessages() {
		VerificationMessages messages = new VerificationMessages();
		VerificationMessages.Commands commands = new VerificationMessages.Commands();
		VerificationMessages.Commands.Confirm confirm = new VerificationMessages.Commands.Confirm();
		confirm.setProtectedActionSelectionRequired("selection-required");
		confirm.setProtectedActionSessionRequired("session-required");
		confirm.setInvalidCode("invalid-code");
		commands.setConfirm(confirm);
		messages.setCommands(commands);
		return messages;
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
