package me.whereareiam.identica.feature.verification.command.executor;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.ConnectionCoordinator;
import me.whereareiam.identica.annotation.Argument;
import me.whereareiam.identica.annotation.AvailableWhilePending;
import me.whereareiam.identica.annotation.Command;
import me.whereareiam.identica.annotation.Definition;
import me.whereareiam.identica.feature.verification.VerificationMessageDefinitions;
import me.whereareiam.identica.feature.verification.VerificationService;
import me.whereareiam.identica.feature.verification.challenge.VerificationChallengeStore;
import me.whereareiam.identica.feature.verification.command.ProtectedActionCommand;
import me.whereareiam.identica.feature.verification.config.VerificationMessages;
import me.whereareiam.identica.feature.verification.model.VerificationDisablePendingState;
import me.whereareiam.identica.feature.verification.model.resolution.VerificationCodeAttempt;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.message.MessageService;
import me.whereareiam.identica.message.SessionMessages;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.model.auth.request.AdvanceRequest;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.message.MessageDefinition;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.model.pipeline.journey.JourneyStateItem;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.state.PipelineInput;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.keystone.Actor;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

@Singleton
public class VerificationConfirmCommand extends ProtectedActionCommand<Void> {
	private static final String AUTHENTICATION = "authentication";

	private final Provider<Messages> coreMessagesProvider;
	private final Provider<VerificationMessages> messagesProvider;
	private final VerificationService verificationService;
	private final VerificationChallengeStore challengeStore;
	private final ConnectionCoordinator connectionCoordinator;
	private final VerificationResultRenderer resultRenderer;
	private final SessionService sessionService;
	private final MessageService messageService;

	@Inject
	public VerificationConfirmCommand(
			Provider<Messages> coreMessagesProvider,
			Provider<VerificationMessages> messagesProvider,
			VerificationService verificationService,
			VerificationChallengeStore challengeStore,
			ConnectionCoordinator connectionCoordinator,
			VerificationResultRenderer resultRenderer,
			SessionService sessionService,
			MessageService messageService
	) {
		super(verificationService);
		this.coreMessagesProvider = coreMessagesProvider;
		this.messagesProvider = messagesProvider;
		this.verificationService = verificationService;
		this.challengeStore = challengeStore;
		this.connectionCoordinator = connectionCoordinator;
		this.resultRenderer = resultRenderer;
		this.sessionService = sessionService;
		this.messageService = messageService;
	}

	@Override
	protected @NotNull SessionService sessionService() {
		return sessionService;
	}

	@Override
	protected @Nullable PlayerMessage currentSessionRequiredMessage() {
		return SessionMessages.CURRENT_SESSION_REQUIRED.create(coreMessagesProvider.get());
	}

	@Override
	protected @NotNull MessageService messageService() {
		return messageService;
	}

	@AvailableWhilePending
	@Definition("verification-confirm")
	@Command("2fa confirm <input>")
	public void confirm(@NotNull Actor sender, @Argument("input") String input) {
		Identity identity = requireIdentity(sender, VerificationMessageDefinitions.PLAYER_ONLY.create(messagesProvider.get()));
		if (identity == null) return;

		if (submitProtectedAction(identity, input))
			return;

		submitChallenge(identity, input);
	}

	private boolean submitProtectedAction(@NotNull Identity identity, @NotNull String input) {
		UUID accountUniqueId = identity.getAccountUniqueId();
		if (accountUniqueId == null) return false;

		VerificationDisablePendingState pendingDisable = challengeStore.findPendingDisable(accountUniqueId).orElse(null);
		if (pendingDisable == null) return false;

		StepUpResult result = confirmStepUp(accountUniqueId, input, "disable-method");
		switch (result.getStatus()) {
			case INVALID_CODE -> send(identity, VerificationMessageDefinitions.CONFIRM_INVALID_CODE, Map.of());
			case CURRENT_SESSION_REQUIRED -> send(identity, VerificationMessageDefinitions.CONFIRM_SESSION_REQUIRED, Map.of());
			case SELECTION_REQUIRED -> send(identity, VerificationMessageDefinitions.CONFIRM_SELECTION_REQUIRED, Map.of());
			case VERIFIED -> {
				challengeStore.clearPendingDisable(accountUniqueId);
				if (pendingDisable.getMethodId().isBlank()) {
					send(identity, VerificationMessageDefinitions.CONFIRM_NO_PENDING, Map.of());
				} else {
					resultRenderer.presentDisableResult(identity, verificationService.disableMethod(accountUniqueId, pendingDisable.getMethodId()));
				}
			}
			default -> {
			}
		}

		return true;
	}

	/**
	 * Hands the code to the pending journey. The pipeline asks {@link ChallengeCode} in the connection's turn, after
	 * every earlier run has finished, so a code typed before its prompt waits for the run that shows the prompt; the
	 * provider's verification step submits the code and answers with the prompt, the invalid-code message or the
	 * no-pending message.
	 */
	private void submitChallenge(@NotNull Identity identity, @NotNull String code) {
		ConnectionDecision decision = connectionCoordinator.advance(AdvanceRequest.builder()
				.connectionUniqueId(identity.getConnectionUniqueId())
				.identity(identity)
				.input(new ChallengeCode(code))
				.build()).toCompletableFuture().join();
		if (decision == null || decision.getStatus() == null) return;

		switch (decision.getStatus()) {
			case WAIT -> sendMessage(identity, decision.getMessage());
			case DENY, REQUIRE_RECONNECT -> disconnect(identity, decision.getMessage());
			case NO_PENDING -> send(identity, VerificationMessageDefinitions.CONFIRM_NO_PENDING, Map.of());
			default -> {
			}
		}
	}

	private void send(
			@NotNull Actor sender,
			@NotNull MessageDefinition<VerificationMessages> definition,
			@NotNull Map<String, String> placeholders
	) {
		sendMessage(sender, definition.create(messagesProvider.get(), placeholders));
	}

	/** A denial disconnects whether or not it comes with a reason to show. */
	private void disconnect(@NotNull Actor sender, @Nullable PlayerMessage message) {
		if (!(sender instanceof Identity identity)) return;

		identity.disconnect(message != null ? messageService.disconnectReason(identity, message) : Component.empty());
	}

	/**
	 * The typed code as input for the pending journey. Only a journey whose authentication challenge waits for a code
	 * takes it; any other journey, such as one still waiting for the password, declines it and leaves the player with
	 * the no-pending message.
	 */
	@RequiredArgsConstructor
	private final class ChallengeCode implements PipelineInput {
		private final @NotNull String code;

		@Override
		public boolean accepts(@NotNull PipelineState state) {
			ScenarioContext scenario = state.getScenario();
			if (scenario == null || !state.has(JourneyStateItem.KEY)) return false;

			UUID accountUniqueId = scenario.getAccountUniqueId();
			if (accountUniqueId == null) return false;

			return verificationService.hasActiveChallenge(accountUniqueId, providerId(scenario), AUTHENTICATION);
		}

		@Override
		public void applyTo(@NotNull PipelineState state) {
			state.put(VerificationCodeAttempt.KEY, new VerificationCodeAttempt(code));
		}

		private static @Nullable String providerId(@NotNull ScenarioContext scenario) {
			return scenario.getProvider() != null ? scenario.getProvider().getProviderId() : null;
		}
	}
}
