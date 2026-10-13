package me.whereareiam.identica.adapter.command.executor;

import com.google.inject.Inject;
import com.google.inject.Provider;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.ConnectionCoordinator;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.annotation.Argument;
import me.whereareiam.identica.annotation.AvailableWhilePending;
import me.whereareiam.identica.annotation.Command;
import me.whereareiam.identica.annotation.Definition;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.model.auth.request.ResumeRequest;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.pipeline.journey.JourneyStateItem;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.model.provider.selection.ProviderClaim;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.provider.selection.ProviderSelector;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.StageType;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import me.whereareiam.keystone.Actor;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;


@RequiredArgsConstructor(onConstructor_ = @Inject)
public class EnrollCommand {
	private final ConnectionCoordinator connectionCoordinator;
	private final Provider<Engine> engineProvider;
	private final Provider<Messages> messagesProvider;
	private final ProviderSelector providerSelector;

	@AvailableWhilePending
	@Definition("enroll")
	@Command("identica enroll <eligibility>")
	public void enroll(
			@NotNull Actor sender, @Argument("eligibility") String providerId) {
		if (providerId == null || providerId.isBlank())
			return;

		ResumeRequest request = ResumeRequest.builder()
				.connectionUniqueId(sender.getUniqueId())
				.identity(sender instanceof Identity identity ? identity : null)
				.input(state -> selectProvider(state, providerId))
				.build();

		ConnectionDecision decision = connectionCoordinator.resume(request)
				.toCompletableFuture()
				.join();

		if (decision == null || decision.getStatus() == null)
			return;

		Messages.Commands.Enroll enrollMessages = messagesProvider.get().getCommands().getEnroll();

		switch (decision.getStatus()) {
			case WAIT -> sendMessage(sender, decision.getMessage());
			case DENY, REQUIRE_RECONNECT -> disconnect(sender, decision.getMessage());
			case NO_PENDING -> sendMessage(sender, enrollMessages.getNoPending());
			case ALLOW -> sendMessage(sender, enrollMessages.getCompleted());
			default -> {
			}
		}
	}

	/**
	 * Adds the player's choice to the selection of the pending registration, runs the chosen provider and restarts its
	 * provider stage. The pipeline applies this to the state of the run the command resumes.
	 */
	private void selectProvider(@NotNull PipelineState state, @NotNull String providerId) {
		JourneyStateItem pending = state.get(JourneyStateItem.KEY).orElse(null);
		if (pending == null) return;

		ScenarioContext context = state.getScenario(PipelineType.REGISTRATION);
		if (context == null) return;

		context.setSelection(providerSelector.claim(
				context.getSelection(),
				List.of(ProviderClaim.of(providerId, ProviderClaimOrigin.PLAYER_CHOICE))
		));
		String username = context.getUsername() != null ? context.getUsername() : "";
		ProviderContext provider = context.getProvider();
		if (provider == null) {
			context.setProvider(ProviderContext.of(providerId, null, username));
		} else {
			provider.setProviderId(providerId);
			if (provider.getProviderUsername().isBlank())
				provider.setProviderUsername(username);
		}

		state.setScenario(context);
		if (state.getPipelineType() == null) {
			state.setPipelineType(PipelineType.REGISTRATION);
		}

		long ttlMs = engineProvider.get().getScenarios().getRegistration().pipelineTtlMillis();
		if (ttlMs > 0) {
			state.put(JourneyStateItem.KEY, new JourneyStateItem(
					pending.getJourneyMode(),
					StageType.PROVIDER.id(),
					0
			), ttlMs);
		}
	}

	private void sendMessage(@NotNull Actor sender, String message) {
		if (message == null || message.isBlank()) return;
		sender.sendMessage(Serializer.serialize(sender, message));
	}

	/** A denial disconnects whether or not it comes with a reason to show. */
	private void disconnect(@NotNull Actor sender, String message) {
		Component component = Serializer.serialize(sender, message == null ? "" : message);
		if (sender instanceof Identity identity)
			identity.disconnect(component);
	}
}
