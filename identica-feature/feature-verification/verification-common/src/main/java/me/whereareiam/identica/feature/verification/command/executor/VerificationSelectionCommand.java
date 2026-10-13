package me.whereareiam.identica.feature.verification.command.executor;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.adapter.command.suggestion.ProviderIdSuggestions;
import me.whereareiam.identica.annotation.Argument;
import me.whereareiam.identica.annotation.Command;
import me.whereareiam.identica.annotation.Definition;
import me.whereareiam.identica.annotation.Suggestions;
import me.whereareiam.identica.feature.verification.VerificationService;
import me.whereareiam.identica.feature.verification.challenge.VerificationChallengeStore;
import me.whereareiam.identica.feature.verification.command.ProtectedActionCommand;
import me.whereareiam.identica.feature.verification.command.suggestion.VerificationMethodSuggestions;
import me.whereareiam.identica.feature.verification.config.VerificationMessages;
import me.whereareiam.identica.feature.verification.model.VerificationDisablePendingState;
import me.whereareiam.identica.feature.verification.model.resolution.VerificationResolutionRequest;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.keystone.Actor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Singleton
public class VerificationSelectionCommand extends ProtectedActionCommand<Void> {
	private final Provider<Messages> coreMessagesProvider;
	private final Provider<VerificationMessages> messagesProvider;
	private final VerificationService verificationService;
	private final VerificationResultRenderer resultRenderer;
	private final VerificationChallengeStore challengeStore;
	private final SessionService sessionService;

	@Inject
	public VerificationSelectionCommand(
			Provider<Messages> coreMessagesProvider,
			Provider<VerificationMessages> messagesProvider,
			VerificationService verificationService,
			VerificationResultRenderer resultRenderer,
			VerificationChallengeStore challengeStore,
			SessionService sessionService
	) {
		super(verificationService);
		this.coreMessagesProvider = coreMessagesProvider;
		this.messagesProvider = messagesProvider;
		this.verificationService = verificationService;
		this.resultRenderer = resultRenderer;
		this.challengeStore = challengeStore;
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

	@Definition("verification-use")
	@Command("2fa use <provider> <method>")
	public void use(
			@NotNull Actor sender,
			@Argument("provider") @Suggestions(ProviderIdSuggestions.KEY) String providerId,
			@Argument("method") @Suggestions(VerificationMethodSuggestions.KEY) String methodId
	) {
		Identity identity = requireIdentity(sender, messagesProvider.get().getCommands().getPlayerOnly());
		if (identity == null) return;
		if (requireCurrentSession(identity) == null) return;
		var accountUniqueId = requireAccountUniqueId(identity);
		if (accountUniqueId == null) return;

		resultRenderer.presentSelectionResult(sender, verificationService.selectMethod(accountUniqueId, providerId, methodId));
	}

	@Definition("verification-disable")
	@Command("2fa disable <method>")
	public void disable(
			@NotNull Actor sender,
			@Argument("method") @Suggestions(VerificationMethodSuggestions.KEY) String methodId
	) {
		Identity identity = requireIdentity(sender, messagesProvider.get().getCommands().getPlayerOnly());
		if (identity == null) return;
		Session session = requireCurrentSession(identity);
		if (session == null) return;
		var accountUniqueId = requireAccountUniqueId(identity);
		if (accountUniqueId == null) return;

		boolean enrolled = verificationService.findEnrollments(accountUniqueId).stream()
				.anyMatch(entry -> entry != null && methodId.equalsIgnoreCase(entry.getMethodId()));
		if (!enrolled) {
			resultRenderer.presentDisableResult(sender, verificationService.disableMethod(accountUniqueId, methodId));
			return;
		}

		challengeStore.putPendingDisable(accountUniqueId, new VerificationDisablePendingState(methodId, System.currentTimeMillis()));
		verificationService.resolveVerification(VerificationResolutionRequest.builder()
				.uniqueId(accountUniqueId)
				.providerId(session.getProviderId() != null ? session.getProviderId() : "")
				.purpose("disable-method")
				.build());

		resultRenderer.presentDisablePrompt(sender, methodId);
	}
}
