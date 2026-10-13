package me.whereareiam.identica.feature.verification.command.executor;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.ConnectionCoordinator;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.annotation.Argument;
import me.whereareiam.identica.annotation.AvailableWhilePending;
import me.whereareiam.identica.annotation.Command;
import me.whereareiam.identica.annotation.Definition;
import me.whereareiam.identica.feature.verification.VerificationService;
import me.whereareiam.identica.feature.verification.command.ProtectedActionCommand;
import me.whereareiam.identica.feature.verification.config.VerificationMessages;
import me.whereareiam.identica.feature.verification.model.VerificationDisablePendingState;
import me.whereareiam.identica.feature.verification.model.config.VerificationSettings;
import me.whereareiam.identica.feature.verification.model.resolution.VerificationCodeAttempt;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.model.auth.request.AdvanceRequest;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.pipeline.journey.JourneyStateItem;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.state.PipelineInput;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.keystone.Actor;
import me.whereareiam.keystone.model.SerializerContent;
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
	private final Provider<VerificationSettings> verificationProvider;
	private final VerificationService verificationService;
	private final PipelineStateStore pipelineStateStore;
	private final ConnectionCoordinator connectionCoordinator;
	private final VerificationResultRenderer resultRenderer;
	private final SessionService sessionService;

	@Inject
	public VerificationConfirmCommand(
			Provider<Messages> coreMessagesProvider,
			Provider<VerificationMessages> messagesProvider,
			Provider<VerificationSettings> verificationProvider,
			VerificationService verificationService,
			PipelineStateStore pipelineStateStore,
			ConnectionCoordinator connectionCoordinator,
			VerificationResultRenderer resultRenderer,
			SessionService sessionService
	) {
		super(verificationService);
		this.coreMessagesProvider = coreMessagesProvider;
		this.messagesProvider = messagesProvider;
		this.verificationProvider = verificationProvider;
		this.verificationService = verificationService;
		this.pipelineStateStore = pipelineStateStore;
		this.connectionCoordinator = connectionCoordinator;
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

	@AvailableWhilePending
	@Definition("verification-confirm")
	@Command("2fa confirm <input>")
	public void confirm(@NotNull Actor sender, @Argument("input") String input) {
		Identity identity = requireIdentity(sender, verificationMessages().getPlayerOnly());
		if (identity == null) return;

		if (submitProtectedAction(identity, input))
			return;

		submitChallenge(identity, input);
	}

	private boolean submitProtectedAction(@NotNull Identity identity, @NotNull String input) {
		VerificationDisablePendingState pendingDisable = findPendingDisable(identity);
		if (pendingDisable == null) return false;

		var accountUniqueId = requireAccountUniqueId(identity);
		if (accountUniqueId == null) return false;

		StepUpResult result = confirmStepUp(accountUniqueId, input, "disable-method");
		switch (result.getStatus()) {
			case INVALID_CODE -> sendMessage(identity, verificationMessages().getConfirm().getInvalidCode(), Map.of());
			case CURRENT_SESSION_REQUIRED -> sendMessage(identity, verificationMessages().getConfirm().getProtectedActionSessionRequired(), Map.of());
			case SELECTION_REQUIRED -> sendMessage(identity, verificationMessages().getConfirm().getProtectedActionSelectionRequired(), Map.of());
			case VERIFIED -> {
				clearPendingDisable(identity);
				if (pendingDisable.getMethodId().isBlank()) {
					sendMessage(identity, verificationMessages().getConfirm().getNoPending(), Map.of());
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
			case WAIT -> sendMessage(identity, decision.getMessage(), Map.of());
			case DENY, REQUIRE_RECONNECT -> disconnect(identity, decision.getMessage());
			case NO_PENDING -> sendMessage(identity, verificationMessages().getConfirm().getNoPending(), Map.of());
			default -> {
			}
		}
	}

	private VerificationMessages.Commands verificationMessages() {
		return messagesProvider.get().getCommands();
	}

	private void sendMessage(@NotNull Actor sender, @Nullable String message, @NotNull Map<String, String> placeholders) {
		if (message == null || message.isBlank()) return;
		sender.sendMessage(Serializer.serialize(SerializerContent.builder()
				.receiver(sender)
				.message(message)
				.placeholders(placeholders)
				.build()));
	}

	/** A denial disconnects whether or not it comes with a reason to show. */
	private void disconnect(@NotNull Actor sender, @Nullable String message) {
		Component component = Serializer.serialize(sender, message == null ? "" : message);
		if (sender instanceof Identity identity)
			identity.disconnect(component);
	}

	private @NotNull PipelineStateReference reference(@NotNull Identity identity) {
		return PipelineStateReference.builder()
				.connectionUniqueId(identity.getConnectionUniqueId())
				.accountUniqueId(identity.getAccountUniqueId())
				.connectionKey(identity.connectionKey())
				.build();
	}

	private @Nullable VerificationDisablePendingState findPendingDisable(@NotNull Identity identity) {
		return pipelineStateStore.find(reference(identity))
				.flatMap(state -> state.get(VerificationDisablePendingState.KEY))
				.orElse(null);
	}

	private void clearPendingDisable(@NotNull Identity identity) {
		PipelineStateReference reference = reference(identity);
		PipelineState state = pipelineStateStore.find(reference).orElse(null);
		if (state == null || state.get(VerificationDisablePendingState.KEY).isEmpty())
			return;

		state.remove(VerificationDisablePendingState.KEY);
		if (state.isEmpty()) {
			pipelineStateStore.clear(reference);
			return;
		}

		long ttlMs = verificationProvider.get().challengeTtlMillis();
		pipelineStateStore.save(reference, state, ttlMs);
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
