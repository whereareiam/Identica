package me.whereareiam.identica.feature.verification.command.executor;

import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.feature.verification.VerificationMethod;
import me.whereareiam.identica.feature.verification.VerificationRegistry;
import me.whereareiam.identica.feature.verification.config.VerificationMessages;
import me.whereareiam.identica.feature.verification.model.VerificationMethodDescriptor;
import me.whereareiam.identica.feature.verification.model.enrollment.VerificationEnrollmentResult;
import me.whereareiam.identica.feature.verification.model.process.VerificationProcessDisplay;
import me.whereareiam.identica.feature.verification.process.VerificationChallengeProcess;
import me.whereareiam.identica.feature.verification.process.VerificationEnrollmentProcess;
import me.whereareiam.identica.feature.verification.type.status.VerificationEnrollmentStatus;
import me.whereareiam.keystone.Actor;
import me.whereareiam.keystone.model.SerializerContent;
import me.whereareiam.keystone.model.SerializerOptions;
import me.whereareiam.keystone.serializer.SerializerEngine;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Verification Result Renderer")
class VerificationResultRendererTest {
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
			String message = content.getMessage();
			for (Map.Entry<String, String> entry : content.getPlaceholders().entrySet()) {
				message = message.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
			}
			return Component.text(message);
		}

		public @NotNull String renderTemplate(@NotNull SerializerContent content) {
			String message = content.getMessage();
			for (Map.Entry<String, String> entry : content.getPlaceholders().entrySet()) {
				message = message.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
			}
			return message;
		}

		@Override
		public @NotNull SerializerOptions.PlaceholderFormat getPlaceholderFormat() {
			return SerializerOptions.PlaceholderFormat.CURLY_BRACES;
		}
	};

	@Test
	@DisplayName("Uses method-owned enrollment display lines")
	void usesMethodOwnedEnrollmentDisplayLines() {
		VerificationResultRenderer presenter = new VerificationResultRenderer(this::messages, registry());
		TestActor actor = new TestActor();

		presenter.presentEnrollmentResult(actor, VerificationEnrollmentResult.builder()
				.status(VerificationEnrollmentStatus.STARTED)
				.methodId("mail")
				.display(VerificationProcessDisplay.builder()
						.lines(List.of(
								"Verification started for {methodDisplayName}",
								"Code sent to {maskedEmail}"
						))
						.placeholders(Map.of("maskedEmail", "j***@example.com"))
						.build())
				.build());

		assertEquals("Verification started for Email\nCode sent to j***@example.com", actor.lastMessage());
	}

	@Test
	@DisplayName("Fails fast when enrollment display is missing")
	void failsFastWhenEnrollmentDisplayMissing() {
		VerificationResultRenderer presenter = new VerificationResultRenderer(this::messages, registry());

		IllegalStateException error = assertThrows(IllegalStateException.class, () ->
				presenter.presentEnrollmentResult(new TestActor(), VerificationEnrollmentResult.builder()
						.status(VerificationEnrollmentStatus.STARTED)
						.methodId("mail")
						.build())
		);

		assertEquals("Verification method mail did not provide enrollment display", error.getMessage());
	}

	@Test
	@DisplayName("Shows nothing and does not fail when the enrollment display has no text")
	void emptiedEnrollmentDisplayShowsNothing() {
		VerificationResultRenderer presenter = new VerificationResultRenderer(this::messages, registry());
		TestActor actor = new TestActor();

		assertDoesNotThrow(() -> presenter.presentEnrollmentResult(actor, VerificationEnrollmentResult.builder()
				.status(VerificationEnrollmentStatus.STARTED)
				.methodId("totp")
				.display(VerificationProcessDisplay.builder().lines(List.of()).build())
				.build()));

		assertNull(actor.lastMessage());
	}

	@Test
	@DisplayName("Shows recovery codes inside the body that names them")
	void recoveryCodesAreShownInsideTheirBody() {
		VerificationMessages messages = messages();
		messages.getCommands().getConfirm().getRecoveryCodes().setBody(List.of("Your codes:", "{entries}"));
		messages.getCommands().getConfirm().getRecoveryCodes().setLayout(VerificationMessages.Commands.Confirm.RecoveryCodes.Layout.SINGLE_COLUMN);
		TestActor actor = new TestActor();

		new VerificationResultRenderer(() -> messages, registry()).presentEnrollmentResult(actor, recoveryCodes());

		assertEquals("Your codes:\nAAAA\nBBBB", actor.lastMessage());
	}

	@Test
	@DisplayName("Shows recovery codes on their own when the body is empty")
	void recoveryCodesAreShownWithoutBody() {
		VerificationMessages messages = messages();
		messages.getCommands().getConfirm().getRecoveryCodes().setBody(List.of());
		messages.getCommands().getConfirm().getRecoveryCodes().setLayout(VerificationMessages.Commands.Confirm.RecoveryCodes.Layout.SINGLE_COLUMN);
		TestActor actor = new TestActor();

		new VerificationResultRenderer(() -> messages, registry()).presentEnrollmentResult(actor, recoveryCodes());

		assertEquals("AAAA\nBBBB", actor.lastMessage());
	}

	@Test
	@DisplayName("Shows recovery codes after a body that does not name them")
	void recoveryCodesAreShownAfterBodyWithoutPlaceholder() {
		VerificationMessages messages = messages();
		messages.getCommands().getConfirm().getRecoveryCodes().setBody(List.of("Keep these safe"));
		messages.getCommands().getConfirm().getRecoveryCodes().setLayout(VerificationMessages.Commands.Confirm.RecoveryCodes.Layout.SINGLE_COLUMN);
		TestActor actor = new TestActor();

		new VerificationResultRenderer(() -> messages, registry()).presentEnrollmentResult(actor, recoveryCodes());

		assertEquals("AAAA\nBBBB", actor.lastMessage());
	}

	private static VerificationEnrollmentResult<?> recoveryCodes() {
		return VerificationEnrollmentResult.builder()
				.status(VerificationEnrollmentStatus.WAITING)
				.methodId("totp")
				.recoveryCodes(List.of("AAAA", "BBBB"))
				.build();
	}

	private VerificationMessages messages() {
		VerificationMessages messages = new VerificationMessages();
		VerificationMessages.Commands commands = new VerificationMessages.Commands();
		VerificationMessages.Commands.Enroll enroll = new VerificationMessages.Commands.Enroll();
		enroll.setUnknownMethod("unknown");
		enroll.setAlreadyEnrolled("already");
		commands.setEnroll(enroll);

		VerificationMessages.Commands.Confirm confirm = new VerificationMessages.Commands.Confirm();
		confirm.setNoPending("no-pending");
		confirm.setInvalidCode("invalid");
		confirm.setProtectedActionSelectionRequired("selection-required");
		confirm.setProtectedActionSessionRequired("session-required");
		confirm.setMethodUnavailable("method-unavailable");
		confirm.setEnabled("enabled");
		confirm.setAutoSelected("auto-selected");
		VerificationMessages.Commands.Confirm.RecoveryCodes recoveryCodes = new VerificationMessages.Commands.Confirm.RecoveryCodes();
		recoveryCodes.setBody(List.of("{entries}"));
		VerificationMessages.Commands.EntryFormat entryFormat = new VerificationMessages.Commands.EntryFormat();
		entryFormat.setFormat("{code}");
		entryFormat.setEmptyFormat("{code}");
		recoveryCodes.setSingleColumnEntry(entryFormat);
		recoveryCodes.setTwoColumnEntry(entryFormat);
		recoveryCodes.setEmpty("empty");
		confirm.setRecoveryCodes(recoveryCodes);
		commands.setConfirm(confirm);

		VerificationMessages.Commands.Use use = new VerificationMessages.Commands.Use();
		use.setProviderNotFound("provider-not-found");
		use.setProviderUnsupported("provider-unsupported");
		use.setProviderVerificationDisabled("provider-verification-disabled");
		use.setMethodNotEnrolled("method-not-enrolled");
		use.setMethodDisabledForProvider("method-disabled-for-provider");
		use.setAlreadySelected("already-selected");
		use.setUpdated("updated");
		commands.setUse(use);

		VerificationMessages.Commands.Disable disable = new VerificationMessages.Commands.Disable();
		disable.setMethodNotEnrolled("disable-method-not-enrolled");
		disable.setProtectedPrompt("disable-protected-prompt");
		disable.setDisabled("disabled");
		commands.setDisable(disable);

		VerificationMessages.Commands.Cancel cancel = new VerificationMessages.Commands.Cancel();
		cancel.setNoPending("cancel-no-pending");
		cancel.setCancelled("cancelled");
		cancel.setCancelledProtectedAction("cancelled-protected");
		commands.setCancel(cancel);
		commands.setPlayerOnly("player-only");
		commands.setNotAllowed("not-allowed");
		messages.setCommands(commands);
		return messages;
	}

	private VerificationRegistry registry() {
		return new VerificationRegistry() {
			@Override
			public void register(@NotNull VerificationMethod handler) {
			}

			@Override
			public void unregister(@NotNull VerificationMethod handler) {
			}

			@Override
			public @NotNull Set<VerificationMethod> values() {
				return Set.of();
			}

			@Override
			public @NotNull Optional<VerificationMethod> find(String id) {
				if (!"mail".equalsIgnoreCase(id))
					return Optional.empty();

				return Optional.of(new VerificationMethod() {
					@Override
					public @NotNull VerificationMethodDescriptor descriptor() {
						return VerificationMethodDescriptor.builder()
								.id("mail")
								.displayName("Email")
								.build();
					}

					@Override
					public @NotNull VerificationEnrollmentProcess<?> enrollment() {
						throw new UnsupportedOperationException();
					}

					@Override
					public @NotNull VerificationChallengeProcess<?> challenge() {
						throw new UnsupportedOperationException();
					}
				});
			}
		};
	}

	private static final class TestActor implements Actor {
		private final AtomicReference<Component> lastMessage = new AtomicReference<>();

		@Override
		public @NotNull UUID getUniqueId() {
			return UUID.randomUUID();
		}

		@Override
		public @NotNull String getUsername() {
			return "PlayerOne";
		}

		@Override
		public void sendMessage(@NotNull Component message) {
			lastMessage.set(message);
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

		private String lastMessage() {
			Component message = lastMessage.get();
			return message == null ? null : PlainTextComponentSerializer.plainText().serialize(message);
		}
	}
}
