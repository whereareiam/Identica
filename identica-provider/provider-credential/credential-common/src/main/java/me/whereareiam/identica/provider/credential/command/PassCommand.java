package me.whereareiam.identica.provider.credential.command;

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
import me.whereareiam.identica.model.auth.request.AdvanceRequest;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.pipeline.journey.JourneyStateItem;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.provider.credential.config.CredentialMessages;
import me.whereareiam.identica.provider.credential.pipeline.CredentialRegistrationAttempt;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.keystone.Actor;
import me.whereareiam.keystone.model.SerializerContent;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PassCommand {
	private final ConnectionCoordinator connectionCoordinator;
	private final PipelineStateStore pipelineStateStore;
	private final Provider<CredentialMessages> messagesProvider;
	private final Provider<Engine> settingsProvider;

	@Definition("pass")
	@Command("pass <password>")
	public void pass(@NotNull Actor sender, @Argument(value = "password", parser = "password") String password) {
		if (!(sender instanceof Identity identity)) return;

		PipelineType pipelineType = pendingPipelineType(identity);
		if (!hasPending(identity)) {
			sendMessage(identity, noPendingMessage(pipelineType));
			return;
		}

		long ttlMs = resolveTtl(pipelineType);
		storeRegistrationAttempt(identity, new CredentialRegistrationAttempt(password, false), ttlMs);
		handleDecision(identity, pipelineType, advanceJourneyMode(identity));
	}

	@Definition("passconfirm")
	@Command("passconfirm <password>")
	public void passConfirm(@NotNull Actor sender, @Argument(value = "password", parser = "password") String repeat) {
		if (!(sender instanceof Identity identity))
			return;

		PipelineType pipelineType = pendingPipelineType(identity);
		if (!hasPending(identity)) {
			sendMessage(identity, noPendingMessage(pipelineType));
			return;
		}

		long ttlMs = resolveTtl(pipelineType);
		storeRegistrationAttempt(identity, new CredentialRegistrationAttempt(repeat, true), ttlMs);
		handleDecision(identity, pipelineType, advanceJourneyMode(identity));
	}

	private void handleDecision(
			@NotNull Identity identity,
			@NotNull PipelineType pipelineType,
			ConnectionDecision decision
	) {
		if (decision == null || decision.getStatus() == null)
			return;

		switch (decision.getStatus()) {
			case WAIT -> sendMessage(identity, decision.getMessage());
			case DENY, REQUIRE_RECONNECT -> disconnect(identity, decision.getMessage());
			case NO_PENDING -> sendMessage(identity, noPendingMessage(pipelineType));
			default -> {
			}
		}
	}

	private ConnectionDecision advanceJourneyMode(@NotNull Identity identity) {
		AdvanceRequest request = AdvanceRequest.builder()
				.connectionUniqueId(identity.getConnectionUniqueId())
				.identity(identity)
				.build();
		return connectionCoordinator.advance(request).toCompletableFuture().join();
	}

	private boolean hasPending(@NotNull Identity identity) {
		PipelineState state = pipelineStateStore.find(reference(identity)).orElse(null);
		if (state == null) return false;
		return state.item(JourneyStateItem.class).isPresent();
	}

	private PipelineType pendingPipelineType(@NotNull Identity identity) {
		PipelineState state = pipelineStateStore.find(reference(identity)).orElse(null);
		PipelineType type = state != null ? state.getPipelineType() : null;
		return type != null ? type : PipelineType.REGISTRATION;
	}

	private String noPendingMessage(@NotNull PipelineType pipelineType) {
		CredentialMessages.Scenario scenario = messagesProvider.get().getScenario();
		if (pipelineType == PipelineType.MIGRATION)
			return scenario.getMigration().getSetup().getStatus().getNoPending();

		return scenario.getRegistration().getStatus().getNoPending();
	}

	private long resolveTtl(@NotNull PipelineType pipelineType) {
		Engine settings = settingsProvider.get();
		if (settings == null) return 0L;

		Engine.Scenarios scenarios = settings.getScenarios();
		if (pipelineType == PipelineType.MIGRATION)
			return scenarios.getMigration().pipelineTtlMillis();
		if (pipelineType == PipelineType.AUTHENTICATION)
			return scenarios.getAuthentication().pipelineTtlMillis();

		return scenarios.getRegistration().pipelineTtlMillis();
	}

	private PipelineStateReference reference(@NotNull Identity identity) {
		return PipelineStateReference.builder()
				.connectionUniqueId(identity.getConnectionUniqueId())
				.accountUniqueId(identity.getAccountUniqueId())
				.connectionKey(identity.connectionKey())
				.build();
	}

	private void storeRegistrationAttempt(
			@NotNull Identity identity,
			@NotNull CredentialRegistrationAttempt attempt,
			long ttlMs
	) {
		if (ttlMs <= 0) return;
		PipelineStateReference reference = reference(identity);
		PipelineState stored = pipelineStateStore.find(reference).orElse(null);
		if (stored == null) return;

		stored.putItem(attempt, ttlMs);
		pipelineStateStore.save(reference, stored, ttlMs);
	}

	private void sendMessage(@NotNull Identity identity, String message) {
		if (message == null || message.isBlank()) return;
		SerializerContent content = SerializerContent.builder()
				.receiver(identity)
				.message(message)
				.build();
		identity.sendMessage(Serializer.serialize(content));
	}

	private void disconnect(@NotNull Identity identity, String message) {
		if (message == null || message.isBlank())
			return;
		identity.disconnect(Serializer.serialize(identity, message));
	}
}
