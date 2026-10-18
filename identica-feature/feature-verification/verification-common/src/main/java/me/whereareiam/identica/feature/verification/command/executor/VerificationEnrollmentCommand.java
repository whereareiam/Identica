package me.whereareiam.identica.feature.verification.command.executor;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.annotation.Argument;
import me.whereareiam.identica.annotation.Command;
import me.whereareiam.identica.annotation.Definition;
import me.whereareiam.identica.annotation.Suggestions;
import me.whereareiam.identica.feature.verification.VerificationInteraction;
import me.whereareiam.identica.feature.verification.VerificationService;
import me.whereareiam.identica.feature.verification.command.ProtectedActionCommand;
import me.whereareiam.identica.feature.verification.command.suggestion.VerificationMethodSuggestions;
import me.whereareiam.identica.feature.verification.config.VerificationMessages;
import me.whereareiam.identica.feature.verification.model.interaction.CodeVerificationInteraction;
import me.whereareiam.identica.feature.verification.model.interaction.SavedVerificationInteraction;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.keystone.Actor;
import me.whereareiam.keystone.model.SerializerContent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

@Singleton
public class VerificationEnrollmentCommand extends ProtectedActionCommand<Void> {
	private final Provider<Messages> coreMessagesProvider;
	private final Provider<VerificationMessages> messagesProvider;
	private final VerificationService verificationService;
	private final VerificationResultRenderer resultRenderer;
	private final SessionService sessionService;

	@Inject
	public VerificationEnrollmentCommand(
			Provider<Messages> coreMessagesProvider,
			Provider<VerificationMessages> messagesProvider,
			VerificationService verificationService,
			VerificationResultRenderer resultRenderer,
			SessionService sessionService
	) {
		super(verificationService);
		this.coreMessagesProvider = coreMessagesProvider;
		this.messagesProvider = messagesProvider;
		this.verificationService = verificationService;
		this.resultRenderer = resultRenderer;
		this.sessionService = sessionService;
	}

	@Override
	protected @NotNull SessionService sessionService() {
		return sessionService;
	}

	@Override
	protected @Nullable String currentSessionRequiredMessage() {
		return coreMessagesProvider.get().getCommands().getCurrentSessionRequired();
	}

	@Definition("verification-enroll")
	@Command("2fa enroll <method>")
	public void enroll(
			@NotNull Actor sender,
			@Argument("method") @Suggestions(VerificationMethodSuggestions.KEY) String methodId
	) {
		Identity identity = requireIdentity(sender, verificationMessages().getPlayerOnly());
		if (identity == null) return;
		Session session = requireCurrentSession(identity);
		if (session == null) return;
		var accountUniqueId = requireAccountUniqueId(identity);
		if (accountUniqueId == null) return;

		resultRenderer.presentEnrollmentResult(sender, verificationService.beginEnrollment(
				accountUniqueId,
				identity.getUsername(),
				session.getProviderId(),
				methodId
		));
	}

	@Definition("verification-enroll-confirm")
	@Command("2fa enroll confirm <input>")
	public void enrollConfirm(@NotNull Actor sender, @Argument("input") String input) {
		Identity identity = requireIdentity(sender, verificationMessages().getPlayerOnly());
		if (identity == null) return;
		var accountUniqueId = requireAccountUniqueId(identity);
		if (accountUniqueId == null) return;

		resultRenderer.presentEnrollmentResult(
				sender,
				verificationService.submitEnrollment(accountUniqueId, interaction(accountUniqueId, input))
		);
	}

	@Definition("verification-enroll-cancel")
	@Command("2fa enroll cancel")
	public void cancel(@NotNull Actor sender) {
		Identity identity = requireIdentity(sender, verificationMessages().getPlayerOnly());
		if (identity == null) return;
		var accountUniqueId = requireAccountUniqueId(identity);
		if (accountUniqueId == null) return;

		if (verificationService.cancelPendingEnrollment(accountUniqueId)) {
			sendMessage(sender, verificationMessages().getCancel().getCancelled(), Map.of());
			return;
		}

		sendMessage(sender, verificationMessages().getCancel().getNoPending(), Map.of());
	}

	private VerificationMessages.Commands verificationMessages() {
		return messagesProvider.get().getCommands();
	}

	private VerificationInteraction interaction(@NotNull java.util.UUID uniqueId, @NotNull String input) {
		if ("saved".equalsIgnoreCase(input.trim()))
			return SavedVerificationInteraction.builder()
					.subjectUniqueId(uniqueId)
					.build();

		return CodeVerificationInteraction.builder()
				.subjectUniqueId(uniqueId)
				.code(input)
				.build();
	}

	private void sendMessage(@NotNull Actor sender, @Nullable String message, @NotNull Map<String, String> placeholders) {
		if (message == null || message.isBlank()) return;
		if (sender instanceof Identity identity) {
			identity.send(message, placeholders);
			return;
		}

		sender.sendMessage(Serializer.serialize(SerializerContent.builder()
				.receiver(sender)
				.message(message)
				.placeholders(placeholders)
				.build()));
	}
}
