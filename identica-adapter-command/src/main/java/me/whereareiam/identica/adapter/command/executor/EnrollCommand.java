package me.whereareiam.identica.adapter.command.executor;

import com.google.inject.Inject;
import com.google.inject.Provider;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.annotation.Argument;
import me.whereareiam.identica.annotation.Command;
import me.whereareiam.identica.annotation.Definition;
import me.whereareiam.identica.connection.ConnectionCoordinator;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.model.auth.request.ResumeRequest;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.pipeline.journey.JourneyStateItem;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.StageType;
import me.whereareiam.identica.type.provider.ProviderOrigin;
import me.whereareiam.keystone.Actor;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public class EnrollCommand {
	private final ConnectionCoordinator connectionCoordinator;
	private final PipelineStateStore pipelineStateStore;
	private final Provider<Engine> engineProvider;
	private final Provider<Messages> messagesProvider;

	@Definition("enroll")
	@Command("identica enroll <eligibility>")
	public void enroll(
			@NotNull Actor sender, @Argument("eligibility") String providerId) {
		if (providerId == null || providerId.isBlank())
			return;

		updatePendingSelection(sender, providerId);

		ResumeRequest request = buildResumeRequest(sender);
		if (request == null)
			return;

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

	private @Nullable ResumeRequest buildResumeRequest(@NotNull Actor sender) {
		UUID connectionUniqueId = sender.getUniqueId();

		return ResumeRequest.builder()
				.connectionUniqueId(connectionUniqueId)
				.identity(sender instanceof Identity identity ? identity : null)
				.build();
	}

	private void updatePendingSelection(@NotNull Actor sender, @NotNull String providerId) {
		UUID connectionUniqueId = sender.getUniqueId();
		String connectionKey = sender instanceof Identity identity
				? identity.connectionKey()
				: null;

		UUID accountUniqueId = null;
		if (sender instanceof Identity identity)
			accountUniqueId = identity.getAccountUniqueId();

		PipelineStateReference reference = PipelineStateReference.builder()
				.connectionUniqueId(connectionUniqueId)
				.accountUniqueId(accountUniqueId)
				.connectionKey(connectionKey)
				.build();

		PipelineState stored = pipelineStateStore.find(reference).orElse(null);
		if (stored == null) return;

		JourneyStateItem pending = stored.item(JourneyStateItem.class).orElse(null);
		if (pending == null) return;

		ScenarioContext context = stored.getScenario(PipelineType.REGISTRATION);
		if (context == null) return;

		String username = context.getUsername() != null ? context.getUsername() : "";
		ProviderContext provider = context.getProvider();
		if (provider == null) {
			context.setProvider(ProviderContext.builder()
					.providerId(providerId)
					.providerUsername(username)
					.source(ProviderOrigin.MANUAL)
					.build());
		} else {
			provider.setProviderId(providerId);
			provider.setSource(ProviderOrigin.MANUAL);
			String providerUsername = provider.getProviderUsername();
			if (providerUsername.isBlank())
				provider.setProviderUsername(username);
		}

		stored.setScenario(context);
		if (stored.getPipelineType() == null) {
			stored.setPipelineType(PipelineType.REGISTRATION);
		}

		long ttlMs = engineProvider.get().getScenarios().getRegistration().pipelineTtlMillis();
		if (ttlMs > 0) {
			stored.putItem(new JourneyStateItem(
					pending.getJourneyMode(),
					StageType.PROVIDER.id(),
					0
			), ttlMs);
			pipelineStateStore.save(reference, stored, ttlMs);
		}
	}

	private void sendMessage(@NotNull Actor sender, String message) {
		if (message == null || message.isBlank()) return;
		sender.sendMessage(Serializer.serialize(sender, message));
	}

	private void disconnect(@NotNull Actor sender, String message) {
		if (message == null || message.isBlank()) return;
		Component component = Serializer.serialize(sender, message);
		if (sender instanceof Identity identity)
			identity.disconnect(component);
	}
}
