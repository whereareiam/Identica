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
			@NotNull Actor sender, @Argument("eligibility") String optionId) {
		if (optionId == null || optionId.isBlank())
			return;

		ResumeRequest request = ResumeRequest.builder()
				.connectionUniqueId(sender.getUniqueId())
				.identity(sender instanceof Identity identity ? identity : null)
				.input(state -> selectOption(state, optionId))
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
	 * Adds the player's choice to the selection of the pending journey, runs the chosen option and restarts its
	 * provider stage. An option is a provider's ID, followed by {@code :<method>} for a provider that offers several
	 * methods. The pipeline applies this to the state of the run the command resumes.
	 */
	private void selectOption(@NotNull PipelineState state, @NotNull String optionId) {
		JourneyStateItem pending = state.get(JourneyStateItem.KEY).orElse(null);
		if (pending == null) return;

		PipelineType pipelineType = state.getPipelineType() != null ? state.getPipelineType() : PipelineType.REGISTRATION;

		ScenarioContext context = state.getScenario(pipelineType);
		if (context == null) return;

		int separator = optionId.indexOf(':');
		String providerId = separator < 0 ? optionId : optionId.substring(0, separator);
		String method = separator < 0 ? null : optionId.substring(separator + 1);

		context.setSelection(providerSelector.claim(
				context.getSelection(),
				List.of(ProviderClaim.of(providerId, ProviderClaimOrigin.PLAYER_CHOICE))
		));
		String username = context.getUsername() != null ? context.getUsername() : "";
		ProviderContext provider = context.getProvider();
		if (provider == null) {
			provider = ProviderContext.of(providerId, null, username);
			context.setProvider(provider);
		} else {
			provider.setProviderId(providerId);
			if (provider.getProviderUsername().isBlank())
				provider.setProviderUsername(username);
		}
		if (provider != null)
			provider.setMethod(method == null || method.isBlank() ? null : method);

		state.setScenario(context);
		state.setPipelineType(pipelineType);

		long ttlMs = scenario(pipelineType).pipelineTtlMillis();
		if (ttlMs > 0)
			state.put(JourneyStateItem.KEY, new JourneyStateItem(StageType.PROVIDER.id(), 0), ttlMs);
	}

	private @NotNull Engine.Scenario scenario(@NotNull PipelineType pipelineType) {
		Engine.Scenarios scenarios = engineProvider.get().getScenarios();
		return switch (pipelineType) {
			case REGISTRATION -> scenarios.getRegistration();
			case MIGRATION -> scenarios.getMigration();
			case AUTHENTICATION -> scenarios.getAuthentication();
		};
	}

	private void sendMessage(@NotNull Actor sender, String message) {
		if (message == null || message.isBlank()) return;
		if (sender instanceof Identity identity) {
			identity.send(message);
			return;
		}

		sender.sendMessage(Serializer.serialize(sender, message));
	}

	/** A denial disconnects whether or not it comes with a reason to show. */
	private void disconnect(@NotNull Actor sender, String message) {
		Component component = Serializer.serialize(sender, message == null ? "" : message);
		if (sender instanceof Identity identity)
			identity.disconnect(component);
	}
}
